# Nightjar licensing

SPDX-License-Identifier: GPL-3.0-or-later

Copyright (C) 2026 Nightjar contributors

Nightjar's original application code, original artwork, procedural audio code and
generated original sounds are free software: you can redistribute them and/or
modify them under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or (at your option) any
later version.

Nightjar is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License in LICENSE for details.

## Third-party material

This grant does not replace third-party licenses. AndroidX, Kotlin, coroutines
and the other application runtime libraries retain their Apache-2.0 licenses and
notices. See THIRD_PARTY_NOTICES.txt and LICENSES/Apache-2.0.txt. Gradle wrapper
scripts and the wrapper JAR retain their existing upstream notices; they are not
relicensed as Nightjar code. Build and test tools retain their respective licenses.

The review checked 112 locked debug/release runtime dependency coordinates
(including metadata artifacts) against cached Maven POMs. All declare Apache-2.0;
ListenableFuture inherits it from guava-parent:26.0-android. The embedded license
texts found in those runtime archives are Apache-2.0. This is a scoped dependency
review, not a legal opinion or a patent/ownership clearance.

## Distribution

When distributing an APK, comply with GPLv3's corresponding-source requirements.
For download distribution, provide equivalent access to the complete corresponding
source for that exact binary, including required build scripts and instructions,
at no further charge. Include required third-party source and notices; publishing
only unrelated or newer application source is not sufficient. See GPLv3 sections
1 and 6 for scope, exceptions and permitted distribution methods.

The APK bundles the GPL text, Apache text and dependency notices, accessible from
Settings → Open-source licenses. Release destinations and a matching source release
still need to be supplied when publishing.

The project previously used Apache-2.0. This change does not revoke rights already
granted for copies distributed under that license. Third-party code is unaffected.

Changing the copyright license does not resolve separate patent, trademark,
privacy, or code-ownership questions. In particular, the smart-alarm patent concern
raised in project-specs.md has not been cleared by this review.

References:
- GNU GPLv3: https://www.gnu.org/licenses/gpl-3.0.en.html
- GNU license application guidance: https://www.gnu.org/licenses/gpl-howto.en.html
- Apache/GPL compatibility: https://apache.org/licenses/GPL-compatibility.html
