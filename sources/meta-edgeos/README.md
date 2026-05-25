# meta-edgeos

Custom Yocto layer for EdgeOS, the headless robotics and edge-AI image for
the NXP i.MX8M Plus EVK (`imx8mp-lpddr4-evk`). Built on top of the NXP
Scarthgap BSP release `imx-6.6.52-2.2.2`.

For the full build instructions, host setup, flash and first-boot
procedure, see the main [INSTRUCTIONS.md](../../INSTRUCTIONS.md) at the
repository root.

## What lives here

| Path | Purpose |
|------|---------|
| `conf/layer.conf` | Layer declaration and priorities |
| `conf/distro/edgeos.conf` | Custom `edgeos` distro on top of `fsl-imx-fb`. Enables CRA-compliance tooling (CVE check, SPDX SBOM, archiver) and the license manifest. |
| `recipes-core/images/edgeos-image-headless.bb` | The production image recipe |
| `recipes-core/apt-compat/` | `apt` / `apt-get` wrapper that maps Debian commands onto opkg |
| `recipes-ai/yolo/python3-ultralytics_8.3.bb` | YOLOv8 inference (ONNX Runtime backend, no PyTorch on-device) |
| `recipes-demo/edgeos-demo/` | `edgeos-detect` (live webcam object detection) and `edgeos-benchmark` |
| `recipes-tools/edgeos-perfctl/` | CPU / GPU / NPU / DDR governor control CLI with a live monitor |
| `recipes-multimedia/libcamera/libcamera_%.bbappend` | Drops the rpi/pisp pipelines that do not exist on i.MX8M Plus |
| `recipes-nnstreamer/nnstreamer/nnstreamer_%.bbappend` | Disables TVM on mx8mp to avoid a multi-hour LLVM build |

## Building

```bash
source setup-environment build-edgeos-headless
bitbake edgeos-image-headless
```

(That is the short version. The full procedure, including `repo init` and
host tuning, is in the top-level [INSTRUCTIONS.md](../../INSTRUCTIONS.md).)

## Licensing note

`python3-ultralytics` is AGPL-3.0. If you redistribute a device that runs
this image, you have to make the complete corresponding source available.
The `archiver` bbclass enabled in `edgeos.conf` captures it automatically
during the build, under `DEPLOY_DIR/sources/`. See
[THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) at the repo root for
the full picture.
