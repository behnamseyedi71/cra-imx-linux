# EdgeOS

A headless Yocto Linux image for the NXP **i.MX8M Plus EVK**, built around
the inference, robotics and CAN stacks that an edge device actually needs.

The goal of this repo is simple: one SD-card image you can flash to the board
and start doing real work, without spending a week fighting the BSP.

The image targets the `imx8mp-lpddr4-evk` and is built on top of the NXP
Scarthgap BSP release (`imx-6.6.52-2.2.2`, kernel 6.6.52). It is headless on
purpose. No compositor, no GUI stack, just SSH and the runtimes you need.

## What's in the image

- TensorFlow Lite 2.16 with the NXP VX delegate, so models can run on the
  i.MX8M Plus NPU
- ONNX Runtime 1.17 with the NXP VsiNPU execution provider
- A pre-installed YOLOv8n ONNX model and a live USB-webcam demo
  (`edgeos-detect`) that runs it on the NPU
- nnstreamer with the TFLite plugin
- OpenCV (Python and C++)
- ROS 2 Humble base stack (`ros-base`)
- GStreamer plus the NXP hardware video codecs
- WiFi and Bluetooth for the IW416 M.2 combo module (firmware, kernel driver,
  wpa-supplicant, BlueZ, ConnMan)
- CAN bus tooling (`can-utils`, `iproute2`)
- `edgeos-perfctl`: a small CLI that pins all four Cortex-A53 cores at
  1800 MHz and switches GPU/NPU/DDR devfreq to performance mode, with a live
  per-core monitor
- `edgeos-benchmark`: CPU, memory, storage and ML inference benchmark
- An `apt` / `apt-get` shim that maps the usual Debian command line onto
  `opkg`, so muscle memory still works
- CRA-compliance artefacts produced on every build: SPDX SBOM, CVE scan
  report, and a full GPL/LGPL/AGPL source archive

Compressed image size is around 1.6 GB. The single biggest piece is ROS 2 at
roughly 150 MB; if you don't need it, drop it from the image recipe.

## Hardware target

- NXP i.MX8M Plus LPDDR4 EVK (`imx8mp-lpddr4-evk`)
- 16 GB SD card or larger
- USB-to-serial cable for the debug console (115200 8N1)
- Optional: USB webcam for the YOLO demo, IW416 M.2 module for WiFi/BT

## Quick start

The full build, flash and first-boot walkthrough is in
[INSTRUCTIONS.md](INSTRUCTIONS.md). The short version:

```bash
git clone https://github.com/behnamseyedi71/cra-imx-linux.git
cd cra-imx-linux

repo init -u https://source.codeaurora.org/external/imx/imx-manifest \
          -b imx-linux-scarthgap -m imx-6.6.52-2.2.2.xml
cp manifests/imx-6.6.52-2.2.2.xml .repo/manifests/imx-6.6.52-2.2.2.xml
repo sync -j8

sudo bin/edgeos-host-setup
source setup-environment build-edgeos-headless
bitbake edgeos-image-headless
```

A first build takes 6 to 12 hours on a 16-core / 40 GB host, longer on
smaller machines. Anything you trip over is very likely already covered in
[BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md), which lists every
error I hit on a fresh Ubuntu 24.04 box and the exact fix that worked.

Flashing the resulting image to an SD card:

```bash
cd build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/
sudo bmaptool copy \
    edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst \
    /dev/sdX
```

Default login on first boot is `root` / `edgeos2026`. Change it before you
put the board on a real network.

## Repo layout

```
cra-imx-linux/
  manifests/                          NXP BSP manifest with meta-ros added
  sources/meta-edgeos/                The only layer in this repo
    conf/distro/edgeos.conf           Custom distro (CRA, archiver, SBOM)
    conf/layer.conf
    recipes-core/images/              Image recipe (edgeos-image-headless)
    recipes-core/apt-compat/          apt -> opkg shim
    recipes-ai/yolo/                  ultralytics 8.3 (ONNX inference backend)
    recipes-demo/edgeos-demo/         edgeos-detect, edgeos-benchmark
    recipes-tools/edgeos-perfctl/     Performance governor CLI
    recipes-multimedia/libcamera/     bbappend dropping rpi/pisp pipelines
    recipes-nnstreamer/nnstreamer/    bbappend dropping TVM
  build-edgeos-headless/conf/         local.conf, bblayers.conf
  bin/                                edgeos-host-setup, edgeos-run-build, repo
  INSTRUCTIONS.md                     Build and flash procedure
  BUILD_TROUBLESHOOTING.md            Every error I hit, with fixes
  THIRD_PARTY_NOTICES.md              License notes for bundled software
  LICENSE                             MIT, for the code in this repo
```

Everything else under `sources/` (`poky/`, `meta-imx/`, `meta-openembedded/`,
`meta-ros/`, etc.) is upstream code pulled in by `repo sync` and is not
tracked in this repository.

## Licensing

The code I wrote in this repository, the `meta-edgeos` layer, the demo and
helper scripts, the build configuration and the documentation, is released
under the MIT license. See [LICENSE](LICENSE).

The built image bundles a lot of third-party software. Two items have real
practical consequences and you should read about them before redistributing
anything:

- **Ultralytics YOLOv8** (`python3-ultralytics`) is **AGPL-3.0**. If you put
  a device that runs it on the market, the corresponding source must be
  made available. The build archives that source automatically into
  `tmp/deploy/sources/`.
- **NXP GPU / NPU / VPU firmware** is proprietary, covered by the NXP
  Software License Agreement. If you publish the wic image publicly, your
  download page has to present and require acceptance of the EULA.

The full breakdown is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Author

Behnam Seyedi - b.seyedi71@gmail.com

Issues and pull requests are welcome.
