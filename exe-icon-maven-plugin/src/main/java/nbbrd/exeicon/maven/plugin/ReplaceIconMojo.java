package nbbrd.exeicon.maven.plugin;

import internal.exeicon.IconEditor;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.graalvm.polyglot.PolyglotException;

/**
 * Cross-platform Windows executable icon replacement.
 */
@Mojo(name = "replace-icon", threadSafe = true)
public final class ReplaceIconMojo extends AbstractMojo {

    /** Original, unsigned Windows launcher. Never modified. */
    @Parameter(property = "exeIcon.inputFile", required = true)
    File inputFile;

    /** ICO containing the replacement images at their desired resolutions. */
    @Parameter(property = "exeIcon.iconFile", required = true)
    File iconFile;

    /** Destination executable, distinct from both input files. */
    @Parameter(property = "exeIcon.outputFile", required = true)
    File outputFile;

    /** Numeric icon group ID; optional when the executable contains only one group ID. */
    @Parameter(property = "exeIcon.iconGroupId")
    Integer iconGroupId;

    /** Skip icon replacement. */
    @Parameter(property = "exeIcon.skip", defaultValue = "false")
    boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Executable icon replacement has been skipped.");
            return;
        }
        if (inputFile == null || iconFile == null || outputFile == null) {
            throw new MojoFailureException("inputFile, iconFile and outputFile are required");
        }
        if (iconGroupId != null && (iconGroupId < 0 || iconGroupId > 65535)) {
            throw new MojoFailureException("iconGroupId must be between 0 and 65535");
        }
        Path input = inputFile.toPath().toAbsolutePath().normalize();
        Path icon = iconFile.toPath().toAbsolutePath().normalize();
        Path output = outputFile.toPath().toAbsolutePath().normalize();
        try {
            requireFile(input);
            requireFile(icon);
            requireDistinct(input, output);
            requireDistinct(icon, output);
            byte[] result = IconEditor.replaceIcon(Files.readAllBytes(input), Files.readAllBytes(icon), iconGroupId);
            Files.createDirectories(output.getParent());
            Path temporary = Files.createTempFile(output.getParent(), ".exe-icon-", ".tmp");
            try {
                Files.write(temporary, result);
                try {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            getLog().info("Executable with replacement icon written to: " + output);
        } catch (PolyglotException ex) {
            throw new MojoFailureException("Cannot replace executable icon: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot replace executable icon in " + input + ": " + ex.getMessage(), ex);
        }
    }

    private static void requireFile(Path path) throws MojoFailureException {
        if (!Files.isRegularFile(path)) {
            throw new MojoFailureException("Not a regular input file: " + path);
        }
    }

    private static void requireDistinct(Path input, Path output) throws IOException, MojoFailureException {
        if (input.equals(output)
                || (Files.exists(output) && Files.isSameFile(input, output))
                || input.toRealPath().equals(resolveDestination(output))) {
            throw new MojoFailureException("outputFile must be distinct from inputFile and iconFile: " + output);
        }
    }

    private static Path resolveDestination(Path output) throws IOException {
        if (Files.exists(output)) {
            return output.toRealPath();
        }
        Path parent = output.getParent();
        return Files.exists(parent) ? parent.toRealPath().resolve(output.getFileName()) : output;
    }
}
