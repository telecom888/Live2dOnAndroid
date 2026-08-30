# -*- coding: utf-8 -*-
import sys

if __name__ == "__main__":
    sys.path.insert(0, r"D:\opencode-proj\Live2dOnAndroid\tools")
    import generate_voices_fix as g
    g.TARGETS = [("soyo", 30), ("soyo", 53), ("tomori", 20)]
    sys.exit(g.main())
