package com.andromedia.cli;

import com.andromedia.common.AndromediaConstants;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
    name = AndromediaConstants.APPLICATION_NAME,
    mixinStandardHelpOptions = true,
    version = "0.1.0-SNAPSHOT",
    description = "Andromedia command-line interface",
    subcommands = {
      AndromediaCommand.VersionCommand.class,
      IndexCommand.class,
      SearchCommand.class
    })
public class AndromediaCommand implements Runnable {

  @Option(
      names = {"-v", "--verbose"},
      description = "Verbose output")
  private boolean verbose;

  @Option(
      names = {"--debug"},
      description = "Enable debug diagnostics")
  private boolean debug;

  public boolean isDebug() {
    return debug;
  }

  @Override
  public void run() {
    if (verbose) {
      System.out.println("Andromedia CLI (verbose mode)");
    } else {
      System.out.println("Andromedia CLI — use --help for available commands");
    }
  }

  @Command(name = "version", description = "Print the Andromedia version")
  static class VersionCommand implements Runnable {

    @Override
    public void run() {
      System.out.println("andromedia 0.1.0-SNAPSHOT");
    }
  }
}
