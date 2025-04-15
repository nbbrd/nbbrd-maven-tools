package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
import lombok.NonNull;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@lombok.Getter
@lombok.Setter
@Mojo(name = "merge-reports", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class MergeReportsMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "", property = "compatibility.reports")
    private List<File> reports;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", property = "compatibility.report.file")
    private File reportFile;

    @ParameterParsing
    private List<Path> toReports() {
        return reports.stream().map(File::toPath).collect(toList());
    }

    @ParameterParsing
    private @NonNull Path toReportFile() {
        return Paths.get(fixUnresolvedProperties(reportFile.toURI()));
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Reports merging has been skipped.");
            return;
        }

        if (reports == null || reports.isEmpty()) {
            getLog().info("No reports to merge.");
            return;
        }

        Compatibility compatibility = toCompatibility();

        List<Path> inputFiles = toReports();
        List<Report> inputs = loadAll(compatibility, inputFiles);

        Path outputFile = toReportFile();
        Report output = compatibility.mergeReports(inputs);

        store(compatibility, outputFile, Report.class, output);
        getLog().info("Merged reports written to " + outputFile);
    }

    @VisibleForTesting
    static List<Report> loadAll(Compatibility compatibility, List<Path> files) throws MojoExecutionException {
        DirectoryStream.Filter<? super Path> filter = compatibility.getParserFilter(Report.class);
        List<Report> result = new ArrayList<>();
        for (Path report : files) {
            if (Files.isRegularFile(report)) {
                result.add(load(compatibility, report, Report.class));
            } else if (Files.isDirectory(report)) {
                try (DirectoryStream<Path> paths = Files.newDirectoryStream(report, filter)) {
                    for (Path path : paths) {
                        if (Files.isRegularFile(path)) {
                            result.add(load(compatibility, path, Report.class));
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to list files in dir " + report, ex);
                }
            }
        }
        return result;
    }
}
