package nbbrd.compatibility.maven.plugin;

import internal.compatibility.Files2;
import internal.compatibility.maven.plugin.MojoParameterParsing;
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
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

@lombok.Getter
@lombok.Setter
@Mojo(name = "merge-reports", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class MergeReportsMojo extends AbstractCompatibilityMojo {

    @Parameter(property = "compatibility.reports")
    private List<File> reports;

    @Parameter(property = "compatibility.reportFile", defaultValue = "${project.build.directory}/compatibility.md")
    private File reportFile;

    @MojoParameterParsing
    private List<Path> toReports() {
        return reports.stream().map(File::toPath).collect(toList());
    }

    @MojoParameterParsing
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
        List<Report> result = new ArrayList<>();
        for (Path path : files) {
            if (Files.isRegularFile(path)) {
                result.add(load(compatibility, path, Report.class));
            } else if (Files.isDirectory(path)) {
                for (Path file : getFilesSortedByName(path, compatibility.getParserFilter(Report.class))) {
                    result.add(load(compatibility, file, Report.class));
                }
            }
        }
        return result;
    }

    private static List<Path> getFilesSortedByName(Path dir, DirectoryStream.Filter<? super Path> filter) throws MojoExecutionException {
        try {
            return Files2.getSortedFiles(dir, filter, Comparator.comparing(Path::toString));
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to list files in dir " + dir, ex);
        }
    }
}
