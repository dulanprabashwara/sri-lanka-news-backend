import os
import glob
import re

search_dir = r"D:\sri-lanka-news\sri-lanka-news-backend\src"
for root, dirs, files in os.walk(search_dir):
    for file in files:
        if file.endswith(".java"):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Use regex to match CreateSourceCommand(... )
            # The last argument was a boolean for 'enabled'.
            new_content = re.sub(r'(CreateSourceCommand\([\s\S]*?IngestionType\.[A-Z_]+,\s*(?:true|false))(\s*\))', r'\1,\n                    new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of())\2', content)
            
            # Special case for the Validation tests
            new_content = re.sub(r'CreateSourceCommand\(\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*null,\s*null,\s*(true|false)\s*\)', r'CreateSourceCommand(\1, \2, \3, null, null, \4, new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of()))', new_content)
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Updated {file}")
