package org.embulk.output.orc;

import com.google.common.base.Throwables;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.embulk.config.TaskReport;
import org.embulk.spi.Exec;
import org.embulk.spi.Page;
import org.embulk.spi.PageReader;
import org.embulk.spi.TransactionalPageOutput;

import java.io.IOException;

class OrcTransactionalPageOutput
        implements TransactionalPageOutput
{
    private final PageReader reader;
    private final Writer writer;

    public OrcTransactionalPageOutput(PageReader reader, Writer writer, PluginTask task)
    {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public void add(Page page)
    {
        synchronized (this) {
            try {
                // int size = page.getStringReferences().size();
                final TypeDescription schema = OrcOutputPlugin.getSchema(reader.getSchema());
                final VectorizedRowBatch batch = schema.createRowBatch();
                // batch.size = size;

                reader.setPage(page);
                while (reader.nextRecord()) {
                    final int row = batch.size++;
                    reader.getSchema().visitColumns(
                            new OrcColumnVisitor(reader, batch, row)
                    );
                    if (batch.size >= batch.getMaxSize()) {
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                }
                if (batch.size != 0) {
                    writer.addRowBatch(batch);
                    batch.reset();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void finish()
    {
        try {
            writer.close();
        }
        catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public void close()
    {
    }

    @Override
    public void abort()
    {
    }

    @Override
    public TaskReport commit()
    {
        return Exec.newTaskReport();
    }
}
