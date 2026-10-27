"""Regression checks for Android tab semantics in the Liquid smoke selector."""
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from liquid_interaction_smoke import navigation
from platform_audit import PACKAGE


def hierarchy(selected="home", disabled=None, unclickable=None, duplicate=False):
    root = ET.Element("hierarchy")
    for label in ("Songlass Karaoke QA", "Play"):
        ET.SubElement(root, "node", {"package": PACKAGE, "text": label})
    for key, label, bounds in (
        ("home", "Home", "[200,1750][400,1900]"),
        ("library", "Library", "[400,1750][600,1900]"),
    ):
        node = ET.SubElement(root, "node", {
            "package": PACKAGE, "enabled": str(key != disabled).lower(),
            "focusable": "true", "selected": str(key == selected).lower(),
            "clickable": str(key != selected and key != unclickable).lower(), "bounds": bounds,
        })
        ET.SubElement(node, "node", {"package": PACKAGE, "text": label})
        if duplicate and key == "home":
            root.append(ET.fromstring(ET.tostring(node)))
    return ET.tostring(root, encoding="unicode")


class FakeAudit:
    viewport = (1000, 2000)

    def __init__(self, xml, output):
        self.xml, self.output = xml, Path(output)

    def shell(self, *args, **kwargs):
        if args[:2] == ("uiautomator", "dump"):
            return "UI hierarchy dumped to: /sdcard/songlass-liquid-smoke.xml"
        return self.xml if args[0] == "cat" else ""


class NavigationSemanticsTest(unittest.TestCase):
    def check(self, xml):
        with tempfile.TemporaryDirectory() as output:
            return navigation(FakeAudit(xml, output), "semantics")

    def test_selected_tab_can_omit_click_action(self):
        for selected in ("home", "library"):
            with self.subTest(selected=selected):
                result = self.check(hierarchy(selected=selected))
                self.assertTrue(result[selected]["selected"])
                self.assertFalse(result["library" if selected == "home" else "home"]["selected"])

    def test_unselected_destination_must_be_clickable(self):
        with self.assertRaises(RuntimeError):
            self.check(hierarchy(unclickable="library"))

    def test_disabled_or_duplicate_selected_tab_is_rejected(self):
        for xml in (hierarchy(disabled="home"), hierarchy(duplicate=True)):
            with self.subTest(xml=xml), self.assertRaises(RuntimeError):
                self.check(xml)


if __name__ == "__main__":
    unittest.main()
