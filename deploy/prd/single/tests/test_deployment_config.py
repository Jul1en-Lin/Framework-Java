import re
import subprocess
import unittest
from pathlib import Path


SINGLE = Path(__file__).resolve().parents[1]
ROOT = SINGLE.parents[2]
BOOTSTRAPS = [
    ROOT / "lien-gateway/src/main/resources/bootstrap.yml",
    *[ROOT / f"lien-{name}/lien-{name}-service/src/main/resources/bootstrap.yml"
      for name in ("admin", "file", "portal")],
]


class DeploymentConfigTest(unittest.TestCase):
    def test_client_credentials_have_no_defaults(self):
        for path in BOOTSTRAPS:
            with self.subTest(service=path.parts[-5]):
                text = path.read_text()
                for key, variable in (("username", "NACOS_USERNAME"),
                                      ("password", "NACOS_PASSWORD")):
                    values = re.findall(rf"^\s+{key}: (.+)$", text, re.M)
                    self.assertEqual(values, [f"${{{variable}}}"] * 2)
                self.assertEqual(text.count("namespace: frameworkjava-${RUN_ENV}"), 2)

    def test_compose_requires_credentials_for_each_service(self):
        text = (SINGLE / "app/docker-compose-app.yml").read_text()
        for variable in ("NACOS_USERNAME", "NACOS_PASSWORD"):
            self.assertEqual(text.count(f"{variable}: ${{{variable}:?{variable} is required}}"), 4)

    def test_example_contains_no_credentials(self):
        for line in (SINGLE / ".env.example").read_text().splitlines():
            if not line or line.startswith("#"):
                continue
            key, value = line.split("=", 1)
            self.assertEqual(value, "8666" if key == "WEB_PORT" else "")

    def test_private_inputs_and_future_topology_are_ignored(self):
        for path in ("single/.env", "single/.env.backup", "single/data/secret",
                     "single/backups/dump.sql", "single/res/sql/db.sql",
                     "single/res/sql/nacosdata.sql", "single/res/sql/nacos-auth.sql",
                     "vm1/app/docker-compose-mid.yml", "vm2/app/docker-compose-mid.yml"):
            result = subprocess.run(["git", "check-ignore", "--quiet", str(SINGLE.parent / path)])
            self.assertEqual(result.returncode, 0, path)
        self.assertNotEqual(subprocess.run([
            "git", "check-ignore", "--quiet", str(SINGLE / ".env.example")
        ]).returncode, 0)

    def test_schema_does_not_seed_an_account(self):
        schema = (SINGLE / "app/mysql/sql/nacos.sql").read_text()
        self.assertNotRegex(schema, r"(?i)INSERT\s+INTO")
        self.assertNotRegex(schema, r"\$2[aby]\$\d{2}\$")

    def test_sql_mount_resolves_to_private_input_directory(self):
        compose = SINGLE / "app/docker-compose-mid.yml"
        source = re.search(r"^\s*- (.+):/opt/res/sql:ro$", compose.read_text(), re.M).group(1)
        self.assertEqual((compose.parent / source).resolve(), SINGLE / "res/sql")
        init = (SINGLE / "app/mysql/init/init.sh").read_text()
        self.assertIn("/opt/res/sql/nacos-auth.sql", init)
        self.assertLess(init.index("for sql in"), init.index("CREATE DATABASE"))


if __name__ == "__main__":
    unittest.main()
