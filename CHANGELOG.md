# clojure-extras-plugin Changelog

## 0.8.1

- Upgrade plugin for IJ 2024.3
- Update Built-in clj-kondo -> v2024.11.14

## 0.8.0
- Upgrade plugin for IJ 2024.2 - Thanks R.A Porter (@coyotesqrl) for making it work :)
- Update Built-in clj-kondo -> v2024.08.01
- Locks plugin to IJ 2024.2 and matching Cursive release; this version will not work with any prior versions of IJ/Cursive
- Upgrades plugin build system

## 0.7.13
- Update Built-in clj-kondo -> v2024.08.01
- Upgrade plugin for IJ 2024.2

## 0.7.12
- Update Built-in clj-kondo -> v2024.03.13
- Upgrade plugin for IJ 2024.1

## 0.7.11
- Update Built-in clj-kondo -> v2023.12.15

## 0.7.10
- Update Built-in clj-kondo -> v2023.10.20
- Upgrade plugin for IJ 2023.3

## 0.7.9
- Update Built-in clj-kondo -> v2023.07.13
- Upgrade plugin for IJ 2023.2

## 0.7.8
- Update Built-in clj-kondo -> v2023.05.26

## 0.7.7
- Update Built-in clj-kondo -> v2023.03.17
- Upgrade plugin for IJ 2023.1

## 0.7.6
- Update Built-in clj-kondo -> v2022.12.10
- Upgrade plugin for IJ 2022.3
- Upgrade gradle -> 7.6

## 0.7.5
- Update Built-in clj-kondo -> v2022.10.05
- Fix ProcessCanceledException logging

## 0.7.4
- Upgrades plugin for IJ 2022.2

## 0.7.3
- Update built-in clj-kondo to v2022.06.22

## 0.7.1
- Update built-in clj-kondo to v2022.05.31

## 0.7.0
- Update built-in clj-kondo to v2022.04.08
- The plugin will now automatically analyze classpath dependencies if the project has a .clj-kondo directory
- Fixed wrong text wrapping in some outputs when inline-evaluating
- Fixed bug with new namespace name linter
- Update plugin to support latest Intellij (v2022.1), thanks @prertik!

## 0.6.1
- Fix binary clj-kondo stderr handling
- Fix random out-of-bounds string error on code inspection

## 0.6.0
- Update built-in clj-kondo to v2022.02.09
- Fix remote repl connection port handling
- Fix bug with unbalanced parens

## 0.5.4
- New option to customize highlighting of head symbols
- Better warning/error highlighting
- More fixes for built-in linting on Windows

## 0.5.2
- Fix namespaced keywords highlighting bugs
- Fix built-in linting on windows

## 0.5.1
- Support to Ansi colors on stdout
- Streamlined namespace highlighter

## 0.5.0
- Option do analyze the project classpath with clj-kondo for better linting results
- Inline eval panel will now resize itself automatically based on content
- Inline eval pretty printing (good for long maps)
- Option to output stdout to REPL console
- Bump clj-kondo version to 2022.01.15

## 0.4.5
- Bump clj-kondo version to 2022.01.13
- Make warnings highlighting less intrusive
- Fix error when the project doesn't have a .clj-kondo config folder
- Fix error linting CLJS files

## 0.4.0
- Better clj-kondo integration performance (to make it even faster tune the AutoReparse Delay Option in Preferences > Code Editing)

## 0.3.0
- Add support to clj-kondo inspections
- Evaluate forms asynchronously to avoid UI Freezes
- Code cleanup

## 0.1.0
- Better nREPL session detection (multiple REPLs support)
- Evaluate forms in the context of its namespace (current file)
- Show evaluated results as syntax highlighted hints (pretty!)

## 0.0.9
- Add support to Intellij IDEA 2021.3

## 0.0.7
- Published to marketplace

## 0.0.6
- Initial public version
