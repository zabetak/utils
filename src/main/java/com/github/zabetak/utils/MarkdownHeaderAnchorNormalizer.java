/*
·*·Licensed·to·the·Apache·Software·Foundation·(ASF)·under·one·or·more
·*·contributor·license·agreements.··See·the·NOTICE·file·distributed·with
·*·this·work·for·additional·information·regarding·copyright·ownership.
·*·The·ASF·licenses·this·file·to·you·under·the·Apache·License,·Version·2.0
·*·(the·"License");·you·may·not·use·this·file·except·in·compliance·with
·*·the·License.··You·may·obtain·a·copy·of·the·License·at
·*
·*·http://www.apache.org/licenses/LICENSE-2.0
·*
·*·Unless·required·by·applicable·law·or·agreed·to·in·writing,·software
·*·distributed·under·the·License·is·distributed·on·an·"AS·IS"·BASIS,
·*·WITHOUT·WARRANTIES·OR·CONDITIONS·OF·ANY·KIND,·either·express·or·implied.
·*·See·the·License·for·the·specific·language·governing·permissions·and
·*·limitations·under·the·License.
·*/
package com.github.zabetak.utils;

import static java.lang.String.format;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A Markdown file (.md) visitor that normalizes anchors pointing to section headers. The
 * normalization is performed using a set of rules that resemble the way that GitHub creates URLs
 * from section headers. The visitor modifies the file in-place and does not retain the original.
 */
public class MarkdownHeaderAnchorNormalizer extends SimpleFileVisitor<Path> {
  private static final Pattern REF_PATTERN =
      Pattern.compile("\\[([^\\]]+)\\]\\(\\{\\{< ref \"#[^\"]+\" >\\}\\}\\)");
  private static final Pattern HEADER_PATTERN = Pattern.compile("#+\\s+(.+)");

  @Override
  public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
    if (!p.toString().endsWith(".md")) {
      return FileVisitResult.CONTINUE;
    }
    Set<String> pageHeaders =
        Files.lines(p)
            .map(HEADER_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(m -> m.group(1))
            .collect(Collectors.toSet());
    Path tmpFile = Files.createTempFile(p.getFileName().toString(), "md_sanitizer");
    try (BufferedWriter writer = Files.newBufferedWriter(tmpFile)) {
      Files.lines(p)
          .forEach(
              line -> {
                Matcher m = REF_PATTERN.matcher(line);
                while (m.find()) {
                  if (pageHeaders.contains(m.group(1))) {
                    String link =
                        format("[%s]({{< ref \"#%s\" >}})", m.group(1), normalize(m.group(1)));
                    line = line.replace(m.group(0), link);
                  }
                }
                try {
                  writer.write(line);
                  writer.newLine();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      Files.move(tmpFile, p, StandardCopyOption.REPLACE_EXISTING);
    }
    return FileVisitResult.CONTINUE;
  }

  /**
   * Normalizes the specified string using similar rules to those that GitHub uses to extract URLs
   * from headers.
   *
   * <p>Normalization rules:
   *
   * <ul>
   *   <li>Turn all characters to lowercase
   *   <li>Remove all special character
   *   <li>Replace spaces with dashes
   * </ul>
   *
   * A special character is a character that does not fall into one of the following categories:
   *
   * <ul>
   *   <li>latin alphabet letters
   *   <li>digits
   *   <li>underscore, dash, and space characters
   * </ul>
   *
   * @param s the string to normalize
   * @return a string that is normalized base on some predefined rules.
   */
  private static String normalize(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9 _\\-]", "").replace(' ', '-');
  }

  public static void main(String[] args) throws IOException {
    Files.walkFileTree(Paths.get(args[0]), new MarkdownHeaderAnchorNormalizer());
  }
}
