package dev.dimitra.bot;

import dev.dimitra.bot.model.ChangedFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileContentFetcher {

    private final GitHubClient gh;

    public FileContentFetcher(GitHubClient gh) {
        this.gh = gh;
    }

    /** Saves head version of changed Java files into out/files/<path>. */
    public int fetchAndSaveJavaFiles(Iterable<ChangedFile> files) throws IOException, InterruptedException {
        int saved = 0;
        for (ChangedFile f : files) {
            if (!f.isJavaFile()) continue;
            if ("removed".equalsIgnoreCase(f.status)) continue; // nothing to fetch at head
            if (f.rawUrl == null || f.rawUrl.isBlank()) continue;

            String content = gh.getText(f.rawUrl);
            File outFile = new File("out/files/" + f.filename);
            // ensure folder exists
            if (!outFile.getParentFile().exists()) {
                Files.createDirectories(outFile.getParentFile().toPath());
            }
            Files.writeString(outFile.toPath(), content, StandardCharsets.UTF_8);
            saved++;
        }
        return saved;
    }
}
