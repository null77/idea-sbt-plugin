// Copyright © 2010, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.sbt.plugin;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.MultiMap;

import java.io.*;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Jamie Madill
 * Date: 12/30/10
 * Time: 3:31 AM
 * Annotate Scala files with errors captured from the SbtErrorCaptureFilter
 */
public class SbtAnnotator implements ExternalAnnotator {

    public static class Error {
        public HighlightSeverity severity;
        public String file;
        public int line;
        public String message;
        public int offset;
        public Error(HighlightSeverity severity, String file, int line, String message) {
            this.severity = severity;
            this.file = file;
            this.line = line;
            this.message = message;
        }
    }

    public static MultiMap<File, Error> errorMap = new ConcurrentMultiMap<File, Error>();

    TextRange getLineTextRange(String text, int line, int offset) {
        int start = 0;
        while (--line > 0) {
            start = text.indexOf("\n", start) + 1;
        }
        int lastIndex = text.indexOf("\n", start) + 1;
        if (lastIndex == 0)
            lastIndex = text.length();

        return new TextRange(start + offset, lastIndex);
    }

    public void annotate(PsiFile file, AnnotationHolder holder) {

        VirtualFile virtualFile = file.getVirtualFile();

        if (virtualFile == null)
            return;

        Collection<Error> errors = errorMap.get(new File(virtualFile.getPath()));

        for (Error error : errors) {
            TextRange range = getLineTextRange(file.getText(), error.line, error.offset);

            if (range.getEndOffset() > range.getStartOffset()) {
                if (error.severity == HighlightSeverity.ERROR) {
                    holder.createErrorAnnotation(range, error.message);
                } else if (error.severity == HighlightSeverity.WARNING) {
                    holder.createWarningAnnotation(range, error.message);
                }
            }
        }
    }
}
