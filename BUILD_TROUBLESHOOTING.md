# EdgeOS Build — Known Errors and Fixes

This document records every error encountered during the first build of `edgeos-image-headless`
on Ubuntu 24.04 (16 cores / 40 GB RAM / NVMe), and the exact fix applied for each one.
Apply all fixes **before** running bitbake for the first time to avoid repeating them.

---

## Fix Checklist (apply before first bitbake)

- [ ] 1. `layer.conf` — correct LAYERDEPENDS names
- [ ] 2. `python3-ultralytics` — use `python_setuptools_build_meta`, not `setuptools3`
- [ ] 3. `python3-ultralytics` — relax setuptools version constraint
- [ ] 4. `python3-ultralytics` — create profile.d dir before writing to it
- [ ] 5. `python3-ultralytics` — remove training shell scripts before packaging
- [ ] 6. `libcamera` bbappend — correct pipeline names
- [ ] 7. `nnstreamer` bbappend — disable TVM for mx8mp
- [ ] 8. `local.conf` — add `rm_work` and per-recipe `PARALLEL_MAKE` caps
- [ ] 9. Host — set overcommit_memory before building (prevents OOM crashes)

---

## Error 1 — Layer dependency names wrong

**Symptom:**
```
ERROR: Layer 'edgeos' depends on layer 'imx-bsp', but this layer is not enabled
ERROR: Layer 'edgeos' depends on layer 'imx-sdk', but this layer is not enabled
```

**Cause:** The NXP BSP layer collection names are `fsl-bsp-release` and `fsl-sdk-release`,
not `imx-bsp` / `imx-sdk`.

**Fix** — `sources/meta-edgeos/conf/layer.conf`:
```
LAYERDEPENDS_edgeos = "core fsl-bsp-release fsl-sdk-release"
```

---

## Error 2 — python3-ultralytics wrong build class

**Symptom:**
```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_compile) failed with exit code '1'
FileNotFoundError: setup.py not found
```

**Cause:** The `setuptools3` bbclass calls `setup.py` directly. Ultralytics 8.x uses
`pyproject.toml` with a `setuptools.build_meta` backend (PEP 517) — there is no `setup.py`.

**Fix** — `sources/meta-edgeos/recipes-ai/yolo/python3-ultralytics_8.3.bb`:
```bitbake
# Wrong:  inherit pypi setuptools3
# Wrong:  inherit pypi python_hatchling
# Correct:
inherit pypi python_setuptools_build_meta
```

---

## Error 3 — setuptools version constraint too new

**Symptom:**
```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_compile) failed with exit code '1'
ERROR: setuptools>=70.0.0 is required; Scarthgap ships 69.1.1
```

**Cause:** `ultralytics/pyproject.toml` declares `requires = ["setuptools>=70.0.0"]`.
Yocto Scarthgap only provides setuptools 69.1.1.

**Fix** — relax the constraint at configure time:
```bitbake
do_configure:prepend() {
    sed -i 's/setuptools>=70\.0\.0/setuptools>=69.0.0/' ${S}/pyproject.toml
}
```

Note: a patch file approach (`.patch`) was tried first but failed with a fuzz QA error
because the pyproject.toml line endings varied. The sed approach is more robust.

---

## Error 4 — do_install fails: profile.d directory does not exist

**Symptom:**
```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_install) failed with exit code '1'
install: cannot create regular file '.../etc/profile.d/ultralytics.sh': No such file or directory
```

**Cause:** `python_setuptools_build_meta` does not create `/etc/profile.d/` during install.

**Fix** — create the directory explicitly:
```bitbake
do_install:append() {
    install -d ${D}${sysconfdir}/profile.d
    echo "export YOLO_SKIP_TORCH_CHECK=1" > ${D}${sysconfdir}/profile.d/ultralytics.sh
}
```

---

## Error 5 — do_package_qa: shell script requires /bin/bash

**Symptom:**
```
ERROR: python3-ultralytics-8.3.119-r0 do_package_qa: QA Issue:
  /usr/lib/python3.12/site-packages/ultralytics/data/scripts/get_imagenet.sh
  contained in package python3-ultralytics requires /bin/bash,
  but no providers found in RDEPENDS:python3-ultralytics? [file-rdeps]
```

**Cause:** The ultralytics package ships training data-download scripts (`get_imagenet.sh`,
`get_coco.sh`, etc.) that have a `#!/bin/bash` shebang. These are not needed on an
inference-only embedded device.

**Fix** — strip them during install:
```bitbake
do_install:append() {
    install -d ${D}${sysconfdir}/profile.d
    echo "export YOLO_SKIP_TORCH_CHECK=1" > ${D}${sysconfdir}/profile.d/ultralytics.sh
    find ${D}${libdir} -path "*/ultralytics/data/scripts/*.sh" -delete
}
```

---

## Error 6 — libcamera do_configure fails: unknown pipeline

**Symptom:**
```
ERROR: Task (.../libcamera_0.5.2.bb:do_configure) failed with exit code '1'
meson.build:XX: error: ... pipeline 'rpi' ... not found
  (also triggered for 'pisp' pipeline)
```

**Cause:** The NXP BSP enables the `rpi` and `pisp` libcamera pipelines by default.
These require `libpisp` which is only available on Raspberry Pi 5 — not on i.MX8MP.

**Fix** — `sources/meta-edgeos/recipes-multimedia/libcamera/libcamera_%.bbappend`:
```bitbake
LIBCAMERA_PIPELINES = "imx8-isi,simple,uvcvideo"
```

Do NOT use `uvcusb` — that name is invalid in libcamera 0.5.2; the correct name is `uvcvideo`.

---

## Error 7 — nnstreamer do_configure fails: TVM not found

**Symptom:**
```
ERROR: Task (.../nnstreamer_2.4.0.bb:do_configure) failed with exit code '1'
CMake Error: TVM library not found
```

**Cause:** The NXP BSP auto-enables TVM in nnstreamer's PACKAGECONFIG for the mx8mp SOC
(`PACKAGECONFIG_SOC:mx8mp-nxp-bsp = "tensorflow-lite tvm"`). TVM requires a multi-hour
LLVM compile (~15 GB, 3–5 hours) and is not needed for this build.

**Fix** — `sources/meta-edgeos/recipes-nnstreamer/nnstreamer/nnstreamer_%.bbappend`:
```bitbake
PACKAGECONFIG_SOC:mx8mp-nxp-bsp = "tensorflow-lite"
```

---

## Error 8 — Build crashes: disk full (100%)

**Symptom:**
```
ERROR: No space left on device
NOTE: Stopping build: disk monitor triggered (STOPTASKS threshold reached)
```

**Cause:** Yocto keeps each recipe's full work directory (`tmp/work/`) by default.
Native tool builds (rust-native, cargo-native, gcc-cross, cmake-native, etc.) each consume
5–10 GB of work-dir space that is never needed again after the recipe is packaged.

**Prevention (add to `local.conf` before building):**
```bitbake
# Auto-delete work dirs after packaging (saves 100–200 GB)
INHERIT += "rm_work"
# Keep these for post-failure inspection:
RM_WORK_EXCLUDE += "onnxruntime tensorflow-lite python3-ultralytics"
```

**Emergency cleanup if disk fills mid-build:**
```bash
# Find and delete the largest completed native tool work dirs
BUILD=build-edgeos-headless/tmp/work
rm -rf $BUILD/x86_64-linux/rust-native/*/
rm -rf $BUILD/x86_64-linux/rust-llvm-native/*/
rm -rf $BUILD/x86_64-linux/cargo-native/*/
rm -rf $BUILD/x86_64-linux/cmake-native/*/
rm -rf $BUILD/x86_64-linux/qemu-native/*/
rm -rf $BUILD/x86_64-linux/gcc-cross-aarch64/*/
# Then resume:
bitbake edgeos-image-headless
```

---

## Error 9 — OOM crash (build killed by kernel)

**Symptom:** bitbake worker process killed mid-compile with no error message, or:
```
virtual memory exhausted: Cannot allocate memory
```

**Cause:** `onnxruntime` uses LLVM-style optimisation passes that can peak at 8–12 GB per
thread. On a 40 GB machine with 8 parallel tasks, this can exceed available RAM.

**Prevention (already in `local.conf`):**
```bitbake
BB_NUMBER_THREADS = "8"
PARALLEL_MAKE     = "-j 4"
PARALLEL_MAKE:pn-onnxruntime                 = "-j 2"
PARALLEL_MAKE:pn-tensorflow-lite             = "-j 4"
PARALLEL_MAKE:pn-tensorflow-lite-vx-delegate = "-j 2"
BB_SCHEDULER = "completion"
```

**Before starting a long build, run once as root:**
```bash
echo 1 | sudo tee /proc/sys/vm/overcommit_memory
sudo sysctl -w vm.swappiness=10
```

`overcommit_memory=1` lets the kernel honour large mmap() calls that Yocto's make and ninja
use internally. The default heuristic (0) can refuse allocations that are actually safe and
trigger early OOM kills.

---

## Skipped / Excluded Packages

| Package | Reason excluded |
|---------|----------------|
| `pytorch` | Training-only; inference done via ONNX Runtime. Adds 10–16 GB build time. |
| `tvm` | Requires LLVM compile (3–5 h). Not needed for TFLite/ORT inference. |
| `packagegroup-imx-ml` | Pulls in pytorch + tvm. Use explicit ML packages instead. |
| `python3-scipy` | No recipe in any enabled layer on Scarthgap. |
| `meta-qt6` | Not needed for headless image. Removed from bblayers.conf. |
| `libpisp` / `rpi` pipeline | RPi5-only. Excluded via libcamera bbappend. |

---

## Build Environment Summary

| Setting | Value | Why |
|---------|-------|-----|
| `BB_NUMBER_THREADS` | 8 | Limits concurrent tasks to keep peak RAM under 35 GB |
| `PARALLEL_MAKE` | `-j 4` | Default make threads per task |
| `PARALLEL_MAKE:pn-onnxruntime` | `-j 2` | Peaks at 8–12 GB/thread at link time |
| `BB_SCHEDULER` | `completion` | Prevents multiple heavy link phases overlapping |
| `INHERIT` | `rm_work` | Deletes work dirs after packaging — saves 100–200 GB |
| `PREMIRRORS` | crates.io → yocto mirror | Avoids crates.io rate-limit failures for Rust packages |

---

## Reproducing the Build from Scratch

```bash
# 1. Install host packages (see INSTRUCTIONS.md)

# 2. Clone and sync
git clone https://github.com/behnamseyedi71/cra-imx-linux.git
cd cra-imx-linux
repo init -u https://source.codeaurora.org/external/imx/imx-manifest \
          -b imx-linux-scarthgap -m imx-6.6.52-2.2.2.xml
cp manifests/imx-6.6.52-2.2.2.xml .repo/manifests/imx-6.6.52-2.2.2.xml
repo sync -j8

# 3. Pre-build host tuning (run as root)
echo 1 | sudo tee /proc/sys/vm/overcommit_memory
sudo sysctl -w vm.swappiness=10

# 4. Set up environment
source setup-environment build-edgeos-headless
cd ..

# 5. Build
cd build-edgeos-headless
bitbake edgeos-image-headless
```

All fixes listed in this document are already applied in the `sources/meta-edgeos/` layer
and `build-edgeos-headless/conf/` files committed to the repository. No manual edits needed.
