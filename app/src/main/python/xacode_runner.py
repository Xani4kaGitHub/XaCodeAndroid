import contextlib
import io
import json
import os
import runpy
import sys
import traceback


def run_file(workspace, relative_path, arguments_json="[]"):
    stdout = io.StringIO()
    stderr = io.StringIO()
    previous_cwd = os.getcwd()
    previous_argv = list(sys.argv)
    inserted = False
    try:
        arguments = json.loads(arguments_json or "[]")
        target = os.path.abspath(os.path.join(workspace, relative_path))
        root = os.path.abspath(workspace)
        if os.path.commonpath([root, target]) != root:
            raise ValueError("Python entry file is outside the project")
        os.chdir(root)
        sys.argv = [target] + [str(value) for value in arguments]
        if root not in sys.path:
            sys.path.insert(0, root)
            inserted = True
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            runpy.run_path(target, run_name="__main__")
        ok = True
    except BaseException:
        traceback.print_exc(file=stderr)
        ok = False
    finally:
        if inserted and root in sys.path:
            sys.path.remove(root)
        sys.argv = previous_argv
        os.chdir(previous_cwd)
    return json.dumps({"ok": ok, "stdout": stdout.getvalue(), "stderr": stderr.getvalue()}, ensure_ascii=False)
