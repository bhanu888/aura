/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.util.javascript.directive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.auraframework.util.javascript.builder.EngineJavascriptBuilder;
import org.auraframework.util.javascript.builder.FrameworkJavascriptBuilder;
import org.auraframework.util.javascript.builder.JavascriptBuilder;
import org.auraframework.util.test.annotation.UnAdaptableTest;
import org.auraframework.util.test.util.UnitTestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

/**
 * Automation for verifying the implementation in DirectiveBasedJavascriptGroupTest
 * {@link DirectiveBasedJavascriptGroup}. Javascript files can be grouped in modules. This helps in keeping the
 * javascript modularized.
 */
@UnAdaptableTest("Gold files will yield different results depending on available classpath")
public class DirectiveBasedJavascriptGroupTest extends UnitTestCase {
    /**
     * Should not be able to specify a Directory as start file for a Javascript group
     */
    @Test
    public void testPassingDirForStartFile() throws Exception {
        try {
            DirectiveBasedJavascriptGroup test = new DirectiveBasedJavascriptGroup("test",
                    getResourceFile("/testdata/"), "javascript", ImmutableList.<DirectiveType<?>> of(DirectiveFactory
                            .getDummyDirectiveType()), EnumSet.of(JavascriptGeneratorMode.TESTING));
            fail("Creating a Directive Based javascript Group by specifying a directory as start file should have failed."
                    + test.getName());
        } catch (IOException e) {
            assertTrue("Add File function failed because of an unexpected error message",
                e.getMessage().startsWith("File did not exist or was not a valid, acceptable file"));
        }
    }

    /**
     * Check the workings of isStale(). isStale() only checks the last modified time stamp of EXISTING files in the
     * group. If new files are added, then isStale() will not reflect the real state of the group. However, if you were
     * to INCLUDE a new js file using a include directive in on of the files in the group , then isStale() would still
     * work.
     */
    @Test
    public void testIsStale() throws Exception {
        File newFile = getResourceFile("/testdata/javascript/testIsStale.js");
        newFile.getParentFile().mkdirs();
        Writer writer = new FileWriter(newFile, false);
        try {
            writer.append(new Long(System.currentTimeMillis()).toString());
            writer.flush();
        } finally {
            writer.close();
        }

        try {
            DirectiveBasedJavascriptGroup test = new DirectiveBasedJavascriptGroup("test", newFile.getParentFile(),
                    newFile.getName(), ImmutableList.<DirectiveType<?>> of(DirectiveFactory.getDummyDirectiveType()),
                    EnumSet.of(JavascriptGeneratorMode.TESTING));
            // Immediately after the javascript group is instantiated, the group
            // is stale because we don't know previous contents.
            assertTrue(test.isStale());
            test.getGroupHash(); // Hash it.
            // Without modification, after that it is un-stale.
            assertFalse("Unmodified group should be un-stale", test.isStale());

            // Update a js file which is part of the group
            writer = new FileWriter(newFile, false);
            writer.append("New time: ");
            writer.append(new Long(System.currentTimeMillis()).toString());
            writer.close();
            assertTrue("An existing file in the group was modified and isStale() could not recognize the modification",
                    test.isStale());
        } finally {
            newFile.delete();
        }
    }

    /**
     * Use the javascript processor to generate javascript files in 5 modes. Gold file the five modes and also verify
     * that the file was not created in the 6th mode.
     * @throws Exception 
     */
    @Test
    public void testJavascriptGenerationProduction() throws Exception {
        doTestJavascriptGeneration(JavascriptGeneratorMode.PRODUCTION);
    }

    @Test
    public void testJavascriptGenerationDevelopment() throws Exception {
        doTestJavascriptGeneration(JavascriptGeneratorMode.DEVELOPMENT);
    }

    private void doTestJavascriptGeneration(JavascriptGeneratorMode mode) throws Exception {
        File file = getResourceFile("/testdata/javascript/testAllKindsOfDirectiveGenerate.js");
        DirectiveBasedJavascriptGroup jg = new DirectiveBasedJavascriptGroup("testDummy", file.getParentFile(),
                file.getName(), ImmutableList.<DirectiveType<?>> of(DirectiveFactory.getMultiLineMockDirectiveType(),
                        DirectiveFactory.getMockDirective(), DirectiveFactory.getDummyDirectiveType()), EnumSet.of(
                        mode));

        jg = Mockito.spy(jg);
        Mockito.when(jg.getLastMod()).thenReturn(1234567890L);


        String mockEngineCompat = "var mock='engine compat';console.log(mock);\n";
        String mockWireCompat = "var mock='wire compat';console.log(mock);\n";

        // mock out getSource for engine to test compat
        JavascriptBuilder builder = Mockito.spy(new EngineJavascriptBuilder(jg.resourceLoader));
        Mockito.when(builder.getSource("lwc/engine/es5/engine.js")).thenReturn(mockEngineCompat);
        Mockito.when(builder.getSource("lwc/engine/es5/engine.min.js")).thenReturn(mockEngineCompat);
        Mockito.when(builder.getSource("lwc/wire-service/es5/wire.js")).thenReturn(mockWireCompat);
        Mockito.when(builder.getSource("lwc/wire-service/es5/wire.min.js")).thenReturn(mockWireCompat);
        Mockito.when(builder.getSource("lwc/proxy-compat/compat.js")).thenReturn("");
        Mockito.when(builder.getSource("lwc/proxy-compat/compat.min.js")).thenReturn("");
        jg.javascriptBuilders = new ArrayList<>();
        jg.javascriptBuilders.add(builder);
        jg.javascriptBuilders.add(new FrameworkJavascriptBuilder());

        File dir = getResourceFile("/testdata/javascript/generated/");

        String genFileName = "testDummy_" + mode.getSuffix() + ".js";
        String genCompatFileName = "testDummy_" + mode.getSuffix() + "_compat" + ".js";

        jg.parse();
        jg.generate(dir, false);
        File genFile = new File(dir, genFileName);
        File genCompatFile = new File(dir, genCompatFileName);
        List<File> filesToCheck = new ArrayList<>();
        filesToCheck.add(genFile);
        filesToCheck.add(genCompatFile);

        for (File fileToCheck: filesToCheck) {
            try {
                if (!fileToCheck.exists()) {
                    fail("Javascript processor failed to create " + fileToCheck.getAbsolutePath());
                } else {
                    StringBuilder fileContents = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new FileReader(fileToCheck));
                    try {
                        String line = reader.readLine();
                        while (line != null) {
                            fileContents.append(line);
                            fileContents.append("\n");
                            line = reader.readLine();
                        }
                    } finally {
                        reader.close();
                    }
                    String fileToCheckContents = fileContents.toString();
                    String fileToCheckFileName = fileToCheck.getName();

                    if (fileToCheckFileName.endsWith("_compat.js")) {
                        assertTrue("compat file should have contents from engine_compat", fileToCheckContents.contains(mockEngineCompat));
                    }
                    goldFileText(fileToCheckContents.toString(), "/" + fileToCheck.getName());
                }
            } finally {
                if (fileToCheck.exists()) {
                    fileToCheck.delete();
                }
            }
        }

        File unExpectedGenFile = new File(dir, "testDummy_test.js");
        assertFalse(
                "javascript processor generated a file for test mode even though the group was not specified to do so.",
                unExpectedGenFile.exists());
    }

    /**
     * Make sure the processor regeneration stops when there are errors in the source file
     */
    @Ignore
    @Test
    public void testJavascriptReGenerationFails() throws Exception {
        File file = getResourceFile("/testdata/javascript/testJavascriptReGenerationFails.js");
        DirectiveBasedJavascriptGroup jg = new DirectiveBasedJavascriptGroup("regenerationFail", file.getParentFile(),
                file.getName(), ImmutableList.<DirectiveType<?>> of(DirectiveFactory.getMockDirective()),
                EnumSet.of(JavascriptGeneratorMode.TESTING));
        try {
            jg.regenerate(getResourceFile("/testdata/javascript/generated/"));
            fail("The test should fail because this source file has a multilinemock directive but the javascript was created with just a mock directive");
        } catch (RuntimeException expected) {
            // This is just an extra check
            String notExpectedGenFile = "regenerationFail_test.js";
            File genFile = new File(getResourceFile("/testdata/javascript/generated/"), notExpectedGenFile);
            assertFalse("The javascript processor should not have created this file.", genFile.exists());
        }

    }
}
