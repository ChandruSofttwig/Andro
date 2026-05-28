package com.andromedia.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.RunLast;

@Component
public class AndromediaCliRunner implements CommandLineRunner {

  private final AndromediaCommand rootCommand;
  private final CliExecutionContext cliExecutionContext;
  private final IFactory factory;
  private final ApplicationContext applicationContext;

  public AndromediaCliRunner(
      AndromediaCommand rootCommand,
      CliExecutionContext cliExecutionContext,
      IFactory factory,
      ApplicationContext applicationContext) {
    this.rootCommand = rootCommand;
    this.cliExecutionContext = cliExecutionContext;
    this.factory = factory;
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(String... args) {
    CommandLine commandLine = new CommandLine(rootCommand, factory);
    commandLine.setExecutionStrategy(
        parseResult -> {
          cliExecutionContext.setDebugEnabled(rootCommand.isDebug());
          return new RunLast().execute(parseResult);
        });
    int exitCode = commandLine.execute(args);
    SpringApplication.exit(applicationContext, () -> exitCode);
  }
}
