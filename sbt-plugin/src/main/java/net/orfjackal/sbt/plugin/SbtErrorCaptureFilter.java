// Copyright © 2010, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.sbt.plugin;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.filters.Filter;
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
    public static final String ERROR_EXPRESSION = "\\s" + FILE_PATH_REGEXP + ":" + NUMBER_REGEXP + ":\\s([^\n]*)";

    private Pattern pattern = Pattern.compile(ERROR_EXPRESSION, Pattern.MULTILINE);

    boolean compiling = false;
    Project project;

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
                for (String file : rescanSet) {
                    rescan(file);
                }
                rescanSet.clear();
                compiling = true;
            }
            return null;
        }

        Matcher match = pattern.matcher(line);

        if (match.find()) {
            String file = match.group(1);
            int errLine = Integer.parseInt(match.group(2));
            SbtAnnotator.errorMap.putValue(new File(file), new SbtAnnotator.Error(file, errLine, match.group(3)));
            rescanSet.add(file);
        }

        return null;
    }
}
