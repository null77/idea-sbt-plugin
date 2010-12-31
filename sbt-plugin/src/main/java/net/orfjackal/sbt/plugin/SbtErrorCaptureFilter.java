// Copyright © 2010, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.sbt.plugin;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.filters.Filter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Jamie Madill
 * Date: 12/29/10
 * Time: 4:29 AM
 * Capture errors from SBT and feed them into SbtAnnotator
 */
public class SbtErrorCaptureFilter implements Filter {

    @NonNls
    private static final String FILE_PATH_REGEXP = "((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)";
    private static final String NUMBER_REGEXP = "([0-9]+)";
    private static final String ERROR_EXPRESSION = "\\[(.*)]\\s" + FILE_PATH_REGEXP + ":" + NUMBER_REGEXP + ":\\s([^\n]*)";
    private static final String OFFSET_EXPRESSION = "\\s(\\s+)\\^";

    private Pattern errorPattern = Pattern.compile(ERROR_EXPRESSION, Pattern.MULTILINE);
    private Pattern offsetPattern = Pattern.compile(OFFSET_EXPRESSION, Pattern.MULTILINE);

    boolean compiling = false;
    Project project;
    SbtAnnotator.Error lastError;

    public SbtErrorCaptureFilter(Project project) {
        this.project = project;
    }

    void rescan(String file) {
        VirtualFileManager manager = VirtualFileManager.getInstance();
        VirtualFile vf = manager.findFileByUrl(VfsUtil.pathToUrl(file));
        if (vf == null) return;
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.findFile(vf);
        if (psiFile == null) return;
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    }

    Set<String> rescanSet = new TreeSet<String>();

    public Result applyFilter(String line, int entireLength) {

        if (line.contains("== compile ==")) {
            if (compiling) {
                compiling = false;
                for (String file : rescanSet) {
                    rescan(file);
                }
            } else {
                SbtAnnotator.errorMap.clear();
                lastError = null;
                for (String file : rescanSet) {
                    rescan(file);
                }
                rescanSet.clear();
                compiling = true;
            }
            return null;
        }

        Matcher match = errorPattern.matcher(line);

        if (match.find()) {
            HighlightSeverity severity = HighlightSeverity.ERROR;
            if (match.group(1).equals("warn")) {
                severity = HighlightSeverity.WARNING;
            }
            String file = match.group(2);
            int errLine = Integer.parseInt(match.group(3));
            String message = match.group(4);
            lastError = new SbtAnnotator.Error(severity, file, errLine, message);
            SbtAnnotator.errorMap.putValue(new File(file), lastError);
            rescanSet.add(file);
        } else {
            match = offsetPattern.matcher(line);
            if (match.find() && lastError != null) {
                lastError.offset = match.group(1).length();
            }
        }



        return null;
    }
}
