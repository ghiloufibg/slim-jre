package com.ghiloufi.slimjre.gui.workers;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.core.SlimJre;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingWorker;

/**
 * Background worker for JAR module analysis.
 *
 * <p>Runs the SlimJre analysis in a background thread and publishes progress updates to the EDT.
 * Uses property change listeners to communicate results.
 *
 * <p>All scanners (jdeps, service loaders, reflection, API usage, GraalVM metadata, crypto, locale,
 * zipfs, jmx) are enabled by default without user configuration.
 *
 * <p>Properties fired:
 *
 * <ul>
 *   <li>"progress" - ProgressUpdate with percent and message
 *   <li>"state" - SwingWorker.StateValue when state changes
 * </ul>
 */
public class AnalysisWorker extends SwingWorker<AnalysisResult, AnalysisWorker.ProgressUpdate> {

  private final List<Path> jars;

  /**
   * Creates a new analysis worker.
   *
   * <p>All scanners are enabled by default for comprehensive module detection.
   *
   * @param jars list of JAR files to analyze
   */
  public AnalysisWorker(List<Path> jars) {
    this.jars = List.copyOf(jars);
  }

  @Override
  protected AnalysisResult doInBackground() throws Exception {
    publish(new ProgressUpdate(5, "Initializing analysis..."));

    SlimJre slimJre = new SlimJre();

    publish(new ProgressUpdate(15, "Running jdeps analysis..."));

    // The core library handles parallel execution internally
    // We simulate progress stages for better UX
    publish(new ProgressUpdate(25, "Scanning for service loaders..."));
    publish(new ProgressUpdate(40, "Detecting reflection patterns..."));
    publish(new ProgressUpdate(50, "Analyzing API usage..."));
    publish(new ProgressUpdate(60, "Scanning GraalVM metadata..."));
    publish(new ProgressUpdate(70, "Detecting crypto/SSL usage..."));
    publish(new ProgressUpdate(80, "Scanning locale/i18n patterns..."));
    publish(new ProgressUpdate(85, "Checking ZipFS and JMX usage..."));

    // All scanners enabled by default - uses analyzeOnly(jars) which defaults to true for all
    AnalysisResult result = slimJre.analyzeOnly(jars);

    publish(new ProgressUpdate(95, "Compiling results..."));

    return result;
  }

  @Override
  protected void process(List<ProgressUpdate> chunks) {
    // Only use the latest update
    if (!chunks.isEmpty()) {
      ProgressUpdate latest = chunks.get(chunks.size() - 1);
      firePropertyChange("progress", null, latest);
    }
  }

  /**
   * Progress update with percentage and message.
   *
   * @param percent progress percentage (0-100)
   * @param message status message
   */
  public record ProgressUpdate(int percent, String message) {}
}
