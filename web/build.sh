#!/bin/sh
# Builds web/index.html (the published preview) from the parts in web/src, in load order:
# styles and markup, icons, data and delivery engine, screens, features, then startup.
cd "$(dirname "$0")/src" && cat head.html icons.js logic.js views_main.js features.js features2.js features3.js boot.js > ../index.html
