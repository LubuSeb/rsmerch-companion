# Upstream references

The Gradle wrapper was obtained from the official RuneLite example-plugin template. Its scripts retain their Apache 2.0 notices and the wrapper configuration retains the upstream distribution checksum. The referenced Gradle distribution includes its license.

RuneLite and its libraries are build dependencies; they are not redistributed in the plugin JAR. The development entry point follows the official `ExternalPluginManager.loadBuiltin` / `RuneLite.main` template.

The accounting and market-analysis implementation was written for this project. GE tax dates, cap and exemption facts were cross-checked against the OSRS Wiki and public Flipping Utilities source. RuneLite history field meanings and GE History widget layout were checked against RuneLite and its public game-interface scripts.

Market requests use the OSRS Wiki real-time prices API. All UI screenshots used in this repository must contain synthetic records, not personal account history.
