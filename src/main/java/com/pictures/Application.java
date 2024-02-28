package com.pictures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Application {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("JPG", "jpg", "png", "mp4", "MP4");
    private static final Set<String> IGNORED_EXTENSIONS = Set.of("gif", "DS_Store", "MP");
    private static Function<JsonNode, Long> AS_LONG = n -> n.asLong();
    private static final List<LookupMeta> LOOKUP_METAS = List.of(
            new LookupMeta<>(List.of("creationTime", "timestamp"), AS_LONG, Metadata::getDate, Metadata::setDate),
            new LookupMeta<>(List.of("photoTakenTime", "timestamp"), AS_LONG, Metadata::getDate, Metadata::setDate)
    );

    private long totalDuplicateFiles;
    private long totalDuplicateSize;

    private Path srcDir;
    private Path destDir;
    private Map<String, Photo> photoMap = new HashMap<>();

    public Application(Path srcDir, Path destDir) {
        this.srcDir = srcDir;
        this.destDir = destDir;
    }

    public static void main(String[] args) {
        String srcDir = args[0];
        String destDir = args[1];
        new Application(Path.of(srcDir), Path.of(destDir)).run();
    }

    public void run() {
        loadFiles();
        checkFiles();
        moveFiles();
    } 

    private void moveFiles() {
        AtomicInteger counter = new AtomicInteger();
        photoMap.forEach((name, photo) -> {
            if (photo.getPath() != null) {
                Path targetDir = getTargetDir(photo.getMetadata());
                Path dest = destDir.resolve(targetDir);
                dest.toFile().mkdirs();
                Path destFile = dest.resolve(photo.getPath().getFileName());
                try {
                    Files.copy(photo.getPath(), destFile);
                    // set file creattion time
                    if (photo.getMetadata().getDate() != null) {
                        Files.setLastModifiedTime(dest.resolve(photo.getPath().getFileName()), FileTime.fromMillis(photo.getMetadata().getDate()));
                        System.out.print(".");
                    }
                    else {
                        System.out.print("x");
                    }

                    if (counter.incrementAndGet()%100 == 0) {
                        System.out.println("\n");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to move file: " + photo.getPath(), e);
                }
            }
        });
    }

    private Path getTargetDir(Metadata metadata) {
        if (metadata.getDate() == null) {
            return Path.of("unknown");
        }
        ZonedDateTime date = Instant.ofEpochSecond(metadata.getDate()).atZone(TimeZone.getDefault().toZoneId());
        return Path.of("" + date.get(ChronoField.YEAR), "" + date.get(ChronoField.MONTH_OF_YEAR));
    }

    private void checkFiles() {
        AtomicInteger metaOnly = new AtomicInteger();
        AtomicInteger noDate = new AtomicInteger();
        AtomicInteger found = new AtomicInteger();
        photoMap.forEach((name, photo) -> {
            if (photo.getPath() == null) {
                metaOnly.incrementAndGet();
            }
            else if (photo.getMetadata().getDate() == null) {
                noDate.incrementAndGet();
            }
            else {
                found.incrementAndGet();
            }
        });

        System.out.println("Found:" + found + ", NoDate:" + noDate + ", MetaOnly:" + metaOnly);
        System.out.println("Duplicate Files:" + totalDuplicateFiles + ", DuplicateSize:" + totalDuplicateSize);
    }

    @SneakyThrows
    private void loadFiles() {
        String unhandledFiles = Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .filter(f -> {
                    String extension = getExtension(f);
                    return !ALLOWED_EXTENSIONS.contains(extension) &&
                            !"json".equals(extension) &&
                            !IGNORED_EXTENSIONS.contains(extension);
                })
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
        if (!unhandledFiles.isEmpty()) {
            throw new RuntimeException("UNHANDLED FILE TYPE: " + String.join(", ", unhandledFiles));
        }

        Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .filter(f -> ALLOWED_EXTENSIONS.contains(getExtension(f)))
                .forEach(this::processImage);
        Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .filter(f -> "json".equals(getExtension(f)))
                .forEach(this::processMeta);
    }

    private String getExtension(Path f) {
        String name = f.getFileName().toString();
        return name.substring(name.lastIndexOf(".") + 1);
    }

    private void processImage(Path path) {
        String filename = path.getFileName().toString();
        Photo photo = photoMap.computeIfAbsent(filename, name -> Photo.builder().path(path).size(path.toFile().length()).build());
        if (photo.getSize() != path.toFile().length()) {
            throw new RuntimeException("DUPLICATE PHOTO SIZE DIFFERS: " + path + " :: " + photo);
        }
        else if (!photo.getPath().equals(path)) {
            totalDuplicateFiles++;
            totalDuplicateSize += photo.getSize();
        }
    }

    @SneakyThrows
    private void processMeta(Path path) {
        String jsonFileName = path.getFileName().toString();
        String filename = jsonFileName.substring(0, jsonFileName.lastIndexOf("."));
        Photo photo = photoMap.computeIfAbsent(filename, n -> Photo.builder().build());
        Metadata metadata = photo.getMetadata();

        JsonNode jsonNode = MAPPER.readTree(path.toFile());

        LOOKUP_METAS.forEach(lm -> get(jsonNode, lm.getPath())
                .ifPresent(n -> {
                    if (lm.getChecker().apply(metadata) == null) {
                        lm.getSetter().accept(metadata, lm.getGetter().apply(n));
                    }
                })
        );
    }

    private Optional<JsonNode> get(JsonNode jsonNode, List<String> keys) {
        for (String key : keys) {
            jsonNode = jsonNode.get(key);
            if (jsonNode == null) {
                break;
            }
        }

        return Optional.ofNullable(jsonNode);
    }
}
