package io.github.ghiloufibg.slimjre.gui.workers;

import io.github.ghiloufibg.slimjre.config.Result;
import io.github.ghiloufibg.slimjre.config.SlimJreConfig;
import io.github.ghiloufibg.slimjre.core.SlimJre;
import java.util.List;
import javax.swing.SwingWorker;

/**
 * Background worker for JRE creation.
 *
 * <p>Runs the SlimJre JRE creation in a background thread and publishes progress updates to the
 * EDT. Uses property change listeners to communicate results.
 *
 * <p>Properties fired:
 *
 * <ul>
 *   <li>"progress" - ProgressUpdate with percent and message
 *   <li>"state" - SwingWorker.StateValue when state changes
 * </ul>
 */
public class CreateJreWorker extends SwingWorker<Result, CreateJreWorker.ProgressUpdate> {

  private final SlimJreConfig config;

  /**
   * Creates a new JRE creation worker.
   *
   * @param config the configuration for JRE creation
   */
  public CreateJreWorker(SlimJreConfig config) {
    this.config = config;
  }

  @Override
  protected Result doInBackground() throws Exception {
    publish(new ProgressUpdate(5, "Validating configuration..."));
    config.validate();

    publish(new ProgressUpdate(10, "Initializing..."));
    SlimJre slimJre = new SlimJre();

    publish(new ProgressUpdate(15, "Running jdeps analysis..."));
    publish(new ProgressUpdate(25, "Scanning service loaders..."));
    publish(new ProgressUpdate(35, "Detecting reflection patterns..."));
    publish(new ProgressUpdate(45, "Analyzing API usage..."));
    publish(new ProgressUpdate(55, "Scanning GraalVM metadata..."));
    publish(new ProgressUpdate(65, "Resolving module dependencies..."));
    publish(new ProgressUpdate(75, "Running jlink (this may take a while)..."));

    // The actual work happens here
    Result result = slimJre.createMinimalJre(config);

    publish(new ProgressUpdate(95, "Finalizing..."));

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
