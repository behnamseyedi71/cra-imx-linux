# Build troubleshooting: every error I hit and how I fixed it

This is the log of every error I ran into on the first clean build of
`edgeos-image-headless` on Ubuntu 24.04 (16 cores, 40 GB RAM, NVMe), plus
the exact fix I applied for each one. All of these fixes are already in the
`meta-edgeos` layer and the `build-edgeos-headless/conf/` files in this
repo, so you should not have to apply any of them by hand. The list is here
so you understand what was going on if you start tweaking things, or if you
hit something similar on a different host.

---

## Fix checklist (applied already, listed for reference)

- [x] 1. `layer.conf` uses the correct `LAYERDEPENDS` names
- [x] 2. `python3-ultralytics` inherits `python_setuptools_build_meta`, not
      `setuptools3`
- [x] 3. `python3-ultralytics` setuptools version constraint relaxed
- [x] 4. `python3-ultralytics` creates `profile.d` before writing into it
- [x] 5. `python3-ultralytics` strips the training shell scripts
- [x] 6. `libcamera` bbappend drops the rpi/pisp pipelines
- [x] 7. `nnstreamer` bbappend disables TVM on mx8mp
- [x] 8. `local.conf` enables `rm_work` and per-recipe `PARALLEL_MAKE` caps
- [x] 9. `bin/edgeos-host-setup` tunes overcommit, swap and swappiness

---

## Error 1: layer dependency names were wrong

Symptom:

```
ERROR: Layer 'edgeos' depends on layer 'imx-bsp', but this layer is not enabled
ERROR: Layer 'edgeos' depends on layer 'imx-sdk', but this layer is not enabled
```

Cause: the NXP BSP layer collection names are `fsl-bsp-release` and
`fsl-sdk-release`, not `imx-bsp` / `imx-sdk`. Easy to get wrong because the
directories are called `meta-imx-bsp` and `meta-imx-sdk`.

Fix in `sources/meta-edgeos/conf/layer.conf`:

```
LAYERDEPENDS_edgeos = "core fsl-bsp-release fsl-sdk-release"
```

---

## Error 2: python3-ultralytics used the wrong build class

Symptom:

```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_compile) failed with exit code '1'
FileNotFoundError: setup.py not found
```

Cause: the `setuptools3` bbclass calls `setup.py` directly. Ultralytics 8.x
uses `pyproject.toml` with a `setuptools.build_meta` backend (PEP 517).
There is no `setup.py` to call.

Fix in `sources/meta-edgeos/recipes-ai/yolo/python3-ultralytics_8.3.bb`:

```bitbake
# Wrong:  inherit pypi setuptools3
# Wrong:  inherit pypi python_hatchling
# Right:
inherit pypi python_setuptools_build_meta
```

---

## Error 3: setuptools version constraint was too new

Symptom:

```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_compile) failed with exit code '1'
ERROR: setuptools>=70.0.0 is required; Scarthgap ships 69.1.1
```

Cause: `ultralytics/pyproject.toml` declares
`requires = ["setuptools>=70.0.0"]`. Scarthgap only ships 69.1.1.

Fix, relax the constraint at configure time:

```bitbake
do_configure:prepend() {
    sed -i 's/setuptools>=70\.0\.0/setuptools>=69.0.0/' ${S}/pyproject.toml
}
```

I tried a proper `.patch` file first but it failed with a fuzz QA error
because the line endings in `pyproject.toml` were not consistent across
releases. The `sed` approach turned out to be more robust.

---

## Error 4: do_install failed because profile.d did not exist

Symptom:

```
ERROR: Task (.../python3-ultralytics_8.3.bb:do_install) failed with exit code '1'
install: cannot create regular file '.../etc/profile.d/ultralytics.sh': No such file or directory
```

Cause: `python_setuptools_build_meta` does not create `/etc/profile.d/`
during install.

Fix, create it explicitly:

```bitbake
do_install:append() {
    install -d ${D}${sysconfdir}/profile.d
    echo "export YOLO_SKIP_TORCH_CHECK=1" > ${D}${sysconfdir}/profile.d/ultralytics.sh
}
```

---

## Error 5: do_package_qa rejected a shell script needing /bin/bash

Symptom:

```
ERROR: python3-ultralytics-8.3.119-r0 do_package_qa: QA Issue:
  /usr/lib/python3.12/site-packages/ultralytics/data/scripts/get_imagenet.sh
  contained in package python3-ultralytics requires /bin/bash,
  but no providers found in RDEPENDS:python3-ultralytics? [file-rdeps]
```

Cause: the ultralytics package ships training data-download scripts
(`get_imagenet.sh`, `get_coco.sh`, etc.) with a `#!/bin/bash` shebang.
None of them are useful on an inference-only embedded device.

Fix, strip them in do_install:

```bitbake
do_install:append() {
    install -d ${D}${sysconfdir}/profile.d
    echo "export YOLO_SKIP_TORCH_CHECK=1" > ${D}${sysconfdir}/profile.d/ultralytics.sh
    find ${D}${libdir} -path "*/ultralytics/data/scripts/*.sh" -delete
}
```

---

## Error 6: libcamera configure failed on unknown pipelines

Symptom:

```
ERROR: Task (.../libcamera_0.5.2.bb:do_configure) failed with exit code '1'
meson.build:XX: error: ... pipeline 'rpi' ... not found
  (also triggered for 'pisp' pipeline)
```

Cause: the NXP BSP enables the `rpi` and `pisp` libcamera pipelines by
default. Both want `libpisp`, which only exists on Raspberry Pi 5. The
i.MX8M Plus does not have it.

Fix in `sources/meta-edgeos/recipes-multimedia/libcamera/libcamera_%.bbappend`:

```bitbake
LIBCAMERA_PIPELINES = "imx8-isi,simple,uvcvideo"
```

Note: the USB video pipeline is `uvcvideo`, not `uvcusb`. The latter name
is invalid in libcamera 0.5.2.

---

## Error 7: nnstreamer configure could not find TVM

Symptom:

```
ERROR: Task (.../nnstreamer_2.4.0.bb:do_configure) failed with exit code '1'
CMake Error: TVM library not found
```

Cause: the NXP BSP auto-enables TVM in nnstreamer's PACKAGECONFIG for the
mx8mp SOC:

```
PACKAGECONFIG_SOC:mx8mp-nxp-bsp = "tensorflow-lite tvm"
```

TVM is a multi-hour LLVM compile (around 15 GB, 3-5 hours) and is not
needed for the TFLite / ONNX Runtime path used in this image.

Fix in `sources/meta-edgeos/recipes-nnstreamer/nnstreamer/nnstreamer_%.bbappend`:

```bitbake
PACKAGECONFIG_SOC:mx8mp-nxp-bsp = "tensorflow-lite"
```

---

## Error 8: build crashed because the disk filled up

Symptom:

```
ERROR: No space left on device
NOTE: Stopping build: disk monitor triggered (STOPTASKS threshold reached)
```

Cause: by default, Yocto keeps each recipe's full work directory under
`tmp/work/`. The native tool builds (rust-native, cargo-native, gcc-cross,
cmake-native, qemu-native, etc.) each take 5 to 10 GB that is never needed
again after the recipe is packaged.

Prevention (already applied in `local.conf`):

```bitbake
# Delete work dirs after packaging. Saves 100-200 GB over a full build.
INHERIT += "rm_work"
# Keep these around for post-mortem if something goes wrong:
RM_WORK_EXCLUDE += "onnxruntime tensorflow-lite python3-ultralytics"
```

If the disk fills mid-build despite that, you can free space manually
without losing progress:

```bash
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

## Error 9: OOM kill in the middle of a heavy compile

Symptom: bitbake worker process killed mid-compile with no error message,
or:

```
virtual memory exhausted: Cannot allocate memory
```

Cause: `onnxruntime` does LLVM-style optimisation passes that can peak at
8-12 GB per thread. On a 32 GB machine, even two parallel onnxruntime link
threads will exhaust RAM.

Prevention, the current defaults tuned for 32 GB hosts:

```bitbake
BB_NUMBER_THREADS = "6"
PARALLEL_MAKE     = "-j 3"
PARALLEL_MAKE:pn-onnxruntime                 = "-j 1"
PARALLEL_MAKE:pn-tensorflow-lite             = "-j 2"
PARALLEL_MAKE:pn-tensorflow-lite-vx-delegate = "-j 1"
BB_SCHEDULER = "completion"
```

Before kicking off a long build, run the host setup script as root once:

```bash
sudo bin/edgeos-host-setup
```

It creates a 32 GB swapfile at `/swapfile_edgeos`, sets
`vm.overcommit_memory=1` so that make and ninja can honour large `mmap()`
calls (the default heuristic refuses safe allocations and triggers spurious
OOM kills), and lowers `vm.swappiness` to 10 so the kernel only spills to
swap under real pressure.

If you still get OOM kills, drop parallelism another notch:

```bitbake
BB_NUMBER_THREADS = "4"
PARALLEL_MAKE     = "-j 2"
```

This roughly doubles the wall-clock build time but is safe on a 32 GB host
with no swap.

---

## Packages I deliberately did not include

| Package | Why excluded |
|---------|--------------|
| `pytorch` | Training-only. Inference is done via ONNX Runtime. Adds 10-16 GB of build time. |
| `tvm` | Requires LLVM compile (3-5 h). Not needed for the TFLite / ORT inference path. |
| `packagegroup-imx-ml` | Pulls in pytorch and tvm. Listed explicit ML packages instead. |
| `python3-scipy` | No recipe in any enabled layer on Scarthgap. |
| `meta-qt6` | Not needed for a headless image. Removed from `bblayers.conf`. |
| `libpisp` / `rpi` pipeline | Raspberry Pi 5 only. Excluded via the libcamera bbappend. |

---

## Build environment summary (32 GB host defaults)

| Setting | Value | Why |
|---------|-------|-----|
| `BB_NUMBER_THREADS` | 6 | Keeps peak RAM under roughly 28 GB |
| `PARALLEL_MAKE` | `-j 3` | Default make threads per task |
| `PARALLEL_MAKE:pn-onnxruntime` | `-j 1` | Peaks at 8-12 GB per thread at link time |
| `PARALLEL_MAKE:pn-tensorflow-lite` | `-j 2` | XNNPack / RUY link, 4-6 GB peak |
| `BB_SCHEDULER` | `completion` | Avoids overlapping multiple heavy link phases |
| `INHERIT` | `rm_work` | Deletes work dirs after packaging, saves 100-200 GB |
| `PREMIRRORS` | crates.io routed via Yocto mirror | Avoids crates.io rate-limit failures for Rust packages |

On a 40+ GB host you can bump `BB_NUMBER_THREADS` to 8 and `PARALLEL_MAKE`
to `-j 4` for a noticeably faster build.

---

## Reproducing the build from scratch

```bash
# 1. Install host packages (see INSTRUCTIONS.md)

# 2. Clone and sync
git clone https://github.com/behnamseyedi71/cra-imx-linux.git
cd cra-imx-linux
repo init -u https://source.codeaurora.org/external/imx/imx-manifest \
          -b imx-linux-scarthgap -m imx-6.6.52-2.2.2.xml
cp manifests/imx-6.6.52-2.2.2.xml .repo/manifests/imx-6.6.52-2.2.2.xml
repo sync -j8

# 3. Host tuning (one shot, as root)
sudo bin/edgeos-host-setup

# 4. Set up the environment
source setup-environment build-edgeos-headless
cd ..

# 5. Build
cd build-edgeos-headless
bitbake edgeos-image-headless
```

All of the fixes documented above are already in the `sources/meta-edgeos/`
layer and the `build-edgeos-headless/conf/` files in this repository. No
manual edits required.
