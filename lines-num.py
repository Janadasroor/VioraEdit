import os

frontend_dir = r"/home/jnd/AndroidStudioProjects/VioraEdit/app/src/main/java/com/janad/vioraedit"
def count_lines_in_ts_files(src_dir=frontend_dir,file_extensions=('.java','.kt')):
    """
    Count non-empty lines in all TypeScript files under the specified directory.

    Args:
        src_dir: Path to the source directory

    Returns:
        Dictionary with file paths and their line counts, and total lines
    """
    if not os.path.exists(src_dir):
        raise FileNotFoundError(f"Directory does not exist: {src_dir}")

    total_lines = 0
    file_counts = {}

    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith(file_extensions):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        lines = [line for line in f if line.strip()]
                        line_count = len(lines)
                        file_counts[file_path] = line_count
                        total_lines += line_count
                except (PermissionError, FileNotFoundError, OSError) as e:
                    print(f"Error reading {file_path}: {e}")

    return file_counts, total_lines


if __name__ == "__main__":
    try:
        file_counts, total = count_lines_in_ts_files("../VioraEdit/app/src/main/java/com/janad/vioraedit", file_extensions=('.java','.kt'))
    except FileNotFoundError as e:
        print(e)
        exit(1)

    print("Java Files Line Count (excluding empty lines):")
    print("-" * 60)
    for file_path, count in sorted(file_counts.items()):
        print(f"{file_path}: {count} lines")
    print("-" * 60)
    print(f"Total non-empty lines: {total}")
    print(f"Total files: {len(file_counts)}")
