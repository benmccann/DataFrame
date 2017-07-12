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

package de.unknownreality.dataframe.frame;

import de.unknownreality.dataframe.DataFrameException;
import de.unknownreality.dataframe.common.NumberUtil;
import de.unknownreality.dataframe.common.StringUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 28.06.2017.
 */
public class StringUtilTest {
    @Test
    public void test() throws DataFrameException {
        Assert.assertEquals("\"test\"", StringUtil.putInQuotes("test", '"'));

        String splitTest1 = "testA,testB,testC";
        String[] result = new String[3];
        StringUtil.splitQuoted(splitTest1,',',result);
        Assert.assertArrayEquals(new String[]{"testA","testB","testC"},result);

        Assert.assertArrayEquals(
                new String[]{"testA","testB","testC"}
                ,StringUtil.splitQuoted("'testA','testB','testC'",','));

        Assert.assertArrayEquals(
                new String[]{"testA","testB","testC"}
                ,StringUtil.splitQuoted("\"testA\",\"testB\",\"testC\"",','));

        Assert.assertArrayEquals(
                new String[]{"testA,testB","testC"}
                ,StringUtil.splitQuoted("'testA,testB',testC",','));



        Assert.assertArrayEquals(
                new String[]{"testA","testB,testC"}
                ,StringUtil.splitQuoted("testA,\"testB,testC\"",','));

        Assert.assertArrayEquals(
                new String[]{"testA"}
                ,StringUtil.splitQuoted("testA",','));

        Assert.assertArrayEquals(
                new String[]{"testA,testB"}
                ,StringUtil.splitQuoted("'testA,testB'",','));

        Assert.assertArrayEquals(
                new String[]{"testB,testC"}
                ,StringUtil.splitQuoted("\"testB,testC\"",','));

        Assert.assertArrayEquals(
                new String[]{}
                ,StringUtil.splitQuoted("",','));

        Assert.assertArrayEquals(
                new String[]{""}
                ,StringUtil.splitQuoted("\"\"",','));

        Assert.assertArrayEquals(
                new String[]{""}
                ,StringUtil.splitQuoted("''",','));


        Assert.assertArrayEquals(
                new String[]{"testA,testB","testC"}
                ,StringUtil.splitQuoted("testA\\,testB,testC",','));

        Assert.assertArrayEquals(
                new String[]{"test\"A\"","testB","testC"}
                ,StringUtil.splitQuoted("test\"A\",testB,testC",','));

        Assert.assertArrayEquals(
                new String[]{"test\"A","testB","testC"}
                ,StringUtil.splitQuoted("test\"A,testB,testC",','));
        Assert.assertArrayEquals(
                new String[]{"\'testA\'","testB","testC"}
                ,StringUtil.splitQuoted("\\'testA\\',testB,testC",','));
    }


}
