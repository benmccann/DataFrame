/*
 *
 *  * Copyright (c) 2017 Alexander Grün
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package de.unknownreality.dataframe.csv;

import de.unknownreality.dataframe.common.DataContainer;
import de.unknownreality.dataframe.common.Row;
import de.unknownreality.dataframe.io.DataWriter;
import de.unknownreality.dataframe.io.FileFormat;
import de.unknownreality.dataframe.io.ReadFormat;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Created by Alex on 17.06.2017.
 */
public class CSVWriter extends DataWriter {
    private CSVSettings settings;

    protected CSVWriter(CSVSettings settings) {
        this.settings = settings;
    }


    @Override
    public void write(OutputStream os, DataContainer<?, ?> dataContainer) {
        if (settings.isGzip()) {
            try {
                os = new GZIPOutputStream(os);
            } catch (IOException e) {
                throw new CSVRuntimeException("error creating gzip output stream", e);
            }
        }
        super.write(os, dataContainer);
    }

    @Override
    public void write(BufferedWriter bufferedWriter, DataContainer<?, ?> dataContainer) {
        try {
            if (settings.isContainsHeader()) {
                if (settings.getHeaderPrefix() != null) {
                    bufferedWriter.write(settings.getHeaderPrefix());
                }
                for (int i = 0; i < dataContainer.getHeader().size(); i++) {
                    bufferedWriter.write(dataContainer.getHeader().get(i).toString());
                    if (i < dataContainer.getHeader().size() - 1) {
                        bufferedWriter.write(settings.getSeparator());
                    }
                }
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
            for (Row row : dataContainer) {
                for (int i = 0; i < row.size(); i++) {
                    Object v = row.get(i);
                    String s;
                    if(settings.isQuoteStrings() && v instanceof String){
                        s = "\""+v+"\"";
                    }
                    else{
                        s = v.toString();
                    }
                    bufferedWriter.write(s);
                    if (i < row.size() - 1) {
                        bufferedWriter.write(settings.getSeparator());
                    }
                }
                bufferedWriter.newLine();
                bufferedWriter.flush();

            }
        } catch (IOException e) {
            throw new CSVRuntimeException("error writing csv", e);
        }
    }

    @Override
    public void write(File file, DataContainer<?, ?> dataContainer) {
        if (settings.isGzip()) {
            try (OutputStream outputStream = new GZIPOutputStream(new FileOutputStream(file))) {
                write(new BufferedWriter(new OutputStreamWriter(outputStream)), dataContainer);
                return;
            } catch (IOException e) {
                throw new CSVRuntimeException(String.format("error writing file '%s'", file.getAbsolutePath()), e);
            }
        }
        super.write(file, dataContainer);
    }


    @Override
    public Map<String, String> getSettings() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("separator", Character.toString(settings.getSeparator()));
        attributes.put("headerPrefix", settings.getHeaderPrefix());
        attributes.put("containsHeader", Boolean.toString(settings.isContainsHeader()));
        attributes.put("gzip", Boolean.toString(settings.isGzip()));
        return attributes;
    }


    @Override
    public ReadFormat getReadFormat() {
        return FileFormat.CSV;
    }
}
