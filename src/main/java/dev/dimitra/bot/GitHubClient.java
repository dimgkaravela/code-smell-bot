package dev.dimitra.bot;

import dev.dimitra.bot.model.ChangedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GitHubClient {
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String token;
    private final String owner;
    private final String repo;

    public GitHubClient(String token, String repository) {
        this.token = token;
        String[] parts = repository.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("REPOSITORY must be owner/repo");
        this.owner = parts[0];
        this.repo = parts[1];
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public List<ChangedFile> listPullRequestFiles(int prNumber) throws IOException, InterruptedException {
        List<ChangedFile> all = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = String.format(
                    "https://api.github.com/repos/%s/%s/pulls/%d/files?per_page=100&page=%d",
                    owner, repo, prNumber, page
            );
             HttpRequest req = baseGet(url).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            ensureOk(resp);
            List<ChangedFile> pageItems = mapper.readValue(resp.body(), new TypeReference<>() {});
            all.addAll(pageItems);
            if (pageItems.size() < 100) break;
            page++;
        }
        return all;
    }
       /** Fetch raw text (e.g., from file.raw_url). */
    public String getText(String url) throws IOException, InterruptedException {
        HttpRequest req = baseGet(url).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(resp);
        return resp.body();
    }

    /** Post a regular PR comment (issues API). */
    public void postIssueComment(int prNumber, String body) throws IOException, InterruptedException {
        String url = String.format("https://api.github.com/repos/%s/%s/issues/%d/comments", owner, repo, prNumber);
        String json = mapper.writeValueAsString(new CommentBody(body));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(resp);
    }

    private HttpRequest.Builder baseGet(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private void ensureOk(HttpResponse<?> resp) throws IOException {
        if (resp.statusCode() >= 300) {
            throw new IOException("GitHub API error " + resp.statusCode() + ": " + resp.body());
        }
    }

    // tiny record for comment body
    private record CommentBody(String body) {}

    //comment sth

}
