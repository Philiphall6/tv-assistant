"""Generate build manifests; audit installs cannot replace the user's launcher or its data."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

source, destination, mode = sys.argv[1:]
ns = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ns[1:-1])
tree = ET.parse(source)
root = tree.getroot()
if mode == "audit":
    package = root.attrib["package"]
    root.set("package", package + ".audit")
    app = root.find("application")
    app.set(ns + "label", "TV Assistant — isolated tests")
    application_name = app.get(ns + "name", "")
    if application_name.startswith("."):
        app.set(ns + "name", package + application_name)
    for activity in app.findall("activity"):
        name = activity.get(ns + "name")
        if name.startswith("."):
            activity.set(ns + "name", package + name)
        for intent in activity.findall("intent-filter"):
            if any(c.get(ns + "name") == "android.intent.category.HOME" for c in intent.findall("category")):
                activity.remove(intent)
elif mode == "tests":
    for instrumentation in root.findall("instrumentation"):
        instrumentation.set(ns + "targetPackage", "fr.maison.tvlauncher.audit")
elif mode != "release":
    raise SystemExit("Unknown manifest mode")
tree.write(destination, encoding="utf-8", xml_declaration=True)
