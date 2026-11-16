"""Guard dependency direction across the application-owned architecture boundaries."""
from pathlib import Path
import re
import unittest
ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'app/src/main/java/dev/pocket/notebook'

class ArchitectureBoundariesTest(unittest.TestCase):
    def test_data_services_do_not_depend_on_facade_or_presentation(self):
        services = ['catalog', 'collection', 'document', 'resource', 'storage', 'transfer', 'demo', 'security']
        for directory in services:
            for path in (SOURCE / 'data' / directory).glob('*.kt'):
                with self.subTest(path=path.name):
                    imports = '\n'.join(re.findall(r'^import .*', path.read_text(), re.M))
                    self.assertNotRegex(imports, r'\.(?:feature|app|web|ui)\.')
                    self.assertNotRegex(imports, r'\b(?:NotebookRepository|NotebookGateway)\b')
                    if directory != 'storage':
                        self.assertNotRegex(path.read_text(), r'\brunBlocking\s*\(')

    def test_domain_contracts_never_reference_implementation_types(self):
        for path in (SOURCE / 'domain').glob('*.kt'):
            with self.subTest(path=path.name):
                self.assertNotRegex(path.read_text(), r'\b(?:NotebookRepository|NotebookDatabase|WebView|DocumentFileStore)\b')

    def test_byte_storage_has_no_repository_dependency(self):
        code = (SOURCE / 'data/storage/Revision.kt').read_text()
        self.assertIn('DocumentFiles', code)
        self.assertNotIn('NotebookRepository', code)

if __name__ == '__main__':
    unittest.main()
