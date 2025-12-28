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
 * <p>Properties fired:
 *
 * <ul>
 *   <li>"progress" - ProgressUpdate with percent and message
 *   <li>"state" - SwingWorker.StateValue when state changes
 * </ul>
 */
public class AnalysisWorker extends SwingWorker<AnalysisResult, AnalysisWorker.ProgressUpdate> {

  private final List<Path> jars;
  private final boolean scanServiceLoaders;
  private final boolean scanGraalVmMetadata;

  /**
   * Creates a new analysis worker.
   *
   * @param jars list of JAR files to analyze
   * @param scanServiceLoaders whether to scan service loader files
   * @param scanGraalVmMetadata whether to scan GraalVM metadata
   */
  public AnalysisWorker(List<Path> jars, boolean scanServiceLoaders, boolean scanGraalVmMetadata) {
    this.jars = List.copyOf(jars);
    this.scanServiceLoaders = scanServiceLoaders;
    this.scanGraalVmMetadata = scanGraalVmMetadata;
  }

  @Override
  protected AnalysisResult doInBackground() throws Exception {
    publish(new ProgressUpdate(5, "Initializing analysis..."));

    SlimJre slimJre = new SlimJre();

    publish(new ProgressUpdate(15, "Running jdeps analysis..."));

    // The core library handles parallel execution internally
    // We simulate progress stages for better UX
    publish(new ProgressUpdate(30, "Scanning for service loaders..."));
    publish(new ProgressUpdate(50, "Detecting reflection patterns..."));
    publish(new ProgressUpdate(65, "Analyzing API usage..."));
    publish(new ProgressUpdate(80, "Scanning GraalVM metadata..."));

    AnalysisResult result = slimJre.analyzeOnly(jars, scanServiceLoaders, scanGraalVmMetadata);

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
