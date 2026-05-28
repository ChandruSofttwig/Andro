package com.andromedia.cli;

public final class AsciiBannerPrinter {

  private AsciiBannerPrinter() {}

  public static void print() {
    System.out.println(
        """
            _              __
           / \\   ____  ____/ /________
          / _ \\ / __ \\/ __  / ___/ __ \\
         / ___ / / / / /_/ / /  / /_/ /
        /_/  |_/_/ /_/\\__,_/_/   \\____/

            local-first ai developer cli
        """);
  }
}
