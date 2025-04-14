package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Report;
import nbbrd.design.VisibleForTesting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@lombok.Getter
@lombok.Setter
@Mojo(name = "merge-reports", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class MergeReportsMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "", property = "compatibility.reports")
    private List<File> reports;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Upstream check has been skipped.");
            return;
        }

        if (reports == null || reports.isEmpty()) {
            getLog().info("No reports to merge.");
            return;
        }

        mergeReports();
    }

    private void mergeReports() throws MojoExecutionException {
        Compatibility compatibility = loadCompatibility();
        try {
            List<Report> input = loadAll(compatibility, toReports());
            Report output = compatibility.merge(input);
            writeReport(compatibility, output);
        } catch (IOException ex) {
            throw new MojoExecutionException(ex);
        }
    }

    private List<Path> toReports() {
        return reports.stream().map(File::toPath).collect(toList());
    }

    @VisibleForTesting
    static List<Report> loadAll(Compatibility compatibility, List<Path> files) throws IOException, MojoExecutionException {
        DirectoryStream.Filter<? super Path> filter = compatibility.getParserFilter(Report.class);
        List<Report> result = new ArrayList<>();
        for (Path report : files) {
            if (Files.isRegularFile(report)) {
                result.add(loadReport(compatibility, report));
            } else if (Files.isDirectory(report)) {
                try (DirectoryStream<Path> paths = Files.newDirectoryStream(report, filter)) {
                    for (Path path : paths) {
                        if (Files.isRegularFile(path)) {
                            result.add(loadReport(compatibility, path));
                        }
                    }
                }
            }
        }
        return result;
    }
}
