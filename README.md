# Clojure Extras Plugin

<!-- Plugin description -->

Custom features added on top of Cursive for Clojure Lovers: inline code evaluation, clj-kondo linting and more!

<!-- Plugin description end -->

## Main Features

- Inline code evaluation: evaluate forms directly in source view (see screenshots)! Just add a custom Keymap for Tools/Evaluate Inline actions menu
- Clj-kondo support: lint your files with clj-kondo (built-in or local binary support)
- An annotator for keyword/symbols namespaces (custom syntax highlighted namespaces ftw!)
- Status bar widget to display current file/namespace (great for zen mode, try it)

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Clojure Extras</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/brcosta/clj-stuff-plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Preferences

Custom highlighting can be configured at <kbd>Settings/Preferences</kbd> > <kbd>Editor</kbd> > <kbd>Color Scheme</kbd> > <kbd>Clojure Extras</kbd>

You can also setup a custom clj-kondo binary, enable/disable inspections, pretty printing and stdout redirection on <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Clojure Extras</kdb>

<img width="909" alt="image" src="https://user-images.githubusercontent.com/1129599/153772377-554e9bdc-6b4e-4418-b7f5-ac37969edfd6.png">

## Tips

To make clj-kondo inspections highlighting faster you can tune the autoreparse delay on <kbd>Settings/Preferences</kbd> > <kbd>Editor</kbd> > <kbd>Autoreparse delay</kbd> 

<img width="898" alt="image" src="https://user-images.githubusercontent.com/1129599/153772517-6ee6b58e-1a03-4c18-8ef3-1195ce4e3eb0.png">


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
