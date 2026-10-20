package org.springboot.backend;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Component
class AnalysisStore {
    static final int LIMIT = 50;
    private final Path root;
    private final JsonMapper json;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    static class Entry {
        final Path directory;
        final ReentrantLock lock = new ReentrantLock();
        Analysis analysis;
        Entry(Path directory, Analysis analysis) { this.directory = directory; this.analysis = analysis; }
        Path workspace() { return directory.resolve("workspace"); }
    }

    AnalysisStore(RepoMedicConfig.Settings settings, JsonMapper json) throws IOException {
        this.root = SafeFiles.privateDirectory(settings.root().resolve("analyses"));
        this.json = json;
        try (var directories = Files.list(root)) {
            for (Path dir : directories.sorted().toList()) {
                if (Files.isSymbolicLink(dir) || !validId(dir.getFileName().toString())) continue;
                Path metadata = dir.resolve("analysis.json");
                if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
                    SafeFiles.deleteTree(dir); // An interrupted ingestion has no usable snapshot.
                    continue;
                }
                try {
                    Analysis a = json.readValue(Files.readString(metadata), Analysis.class);
                    if (dir.getFileName().toString().equals(a.id)) entries.put(a.id, new Entry(dir, a));
                } catch (RuntimeException ignored) { SafeFiles.deleteTree(dir); }
            }
        }
        while (entries.size() > LIMIT) pruneOldest();
    }

    private static boolean validId(String id) { return id != null && id.matches("[a-f0-9-]{36}"); }

    Analysis create(Function<Entry, Analysis> work) {
        Entry entry;
        synchronized (entries) {
            if (entries.size() >= LIMIT && !pruneOldest())
                throw new ApiException(503, "busy", "All analysis slots are busy. Retry shortly.");
            Analysis a = new Analysis();
            a.id = UUID.randomUUID().toString();
            entry = new Entry(root.resolve(a.id), a);
            entry.lock.lock();
            try { SafeFiles.privateDirectory(entry.workspace()); }
            catch (IOException e) { entry.lock.unlock(); throw storageFailure(); }
            entries.put(a.id, entry);
        }
        try {
            Analysis result = work.apply(entry);
            save(entry);
            return copy(result);
        } catch (RuntimeException e) {
            synchronized (entries) { entries.remove(entry.analysis.id); }
            try { SafeFiles.deleteTree(entry.directory); } catch (IOException ignored) { }
            throw e;
        } finally { entry.lock.unlock(); }
    }

    Analysis read(String id) { return locked(id, e -> copy(e.analysis)); }

    <T> T locked(String id, Function<Entry, T> work) {
        if (!validId(id)) throw notFound();
        Entry entry;
        synchronized (entries) { entry = entries.get(id); }
        if (entry == null) throw notFound();
        entry.lock.lock();
        try {
            synchronized (entries) { if (entries.get(id) != entry) throw notFound(); }
            return work.apply(entry);
        } finally { entry.lock.unlock(); }
    }

    List<Analysis> list() {
        List<String> ids;
        synchronized (entries) { ids = new ArrayList<>(entries.keySet()); }
        List<Analysis> result = new ArrayList<>();
        for (String id : ids) {
            try { result.add(read(id)); } catch (ApiException e) { if (e.status() != 404) throw e; }
        }
        result.sort(Comparator.comparing((Analysis a) -> a.createdAt).reversed());
        return result;
    }

    void save(Entry entry) {
        try {
            Path temporary = entry.directory.resolve("analysis.json.tmp");
            Files.writeString(temporary, json.writeValueAsString(entry.analysis),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary, entry.directory.resolve("analysis.json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, entry.directory.resolve("analysis.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { throw storageFailure(); }
    }

    Analysis copy(Analysis a) { return json.readValue(json.writeValueAsString(a), Analysis.class); }

    // Caller holds the registry monitor; active analyses are never pruned.
    private boolean pruneOldest() {
        List<Entry> oldest = entries.values().stream().sorted(Comparator.comparing(
                e -> e.analysis.createdAt == null ? "" : e.analysis.createdAt)).toList();
        for (Entry entry : oldest) {
            if (!entry.lock.tryLock()) continue;
            try {
                SafeFiles.deleteTree(entry.directory);
                entries.remove(entry.analysis.id);
                return true;
            } catch (IOException e) { throw storageFailure(); }
            finally { entry.lock.unlock(); }
        }
        return false;
    }

    private static ApiException notFound() { return new ApiException(404, "not_found", "Analysis not found"); }
    static ApiException storageFailure() { return new ApiException(500, "storage_error", "Unable to access analysis storage"); }
}
