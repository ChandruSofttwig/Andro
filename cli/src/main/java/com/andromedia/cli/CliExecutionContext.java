package com.andromedia.cli;

import org.springframework.stereotype.Component;

@Component
public class CliExecutionContext {

  private boolean debugEnabled;

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public void setDebugEnabled(boolean debugEnabled) {
    this.debugEnabled = debugEnabled;
  }
}
