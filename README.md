# MKM-Core

This is a preview release of a kinetic simulation project, containing only 
partial source code from an earlier version and mainly focus on micro-kinetics.
For in-depth feature development or code modifications, please wait for the full 
officially release of this project.


## Environment

It is recommended to use Java 1.8.

While higher versions of Java may often work, we do not guarantee compatibility
and issues may occur.

## Run

Prior to running, please prepare your `setup file`. To start the application,
use the command below. For a complete list of available options, execute the
command with the `--help` flag.

`java -jar mkm-core-x.x.x-alpha-all.jar --help`

## Build

Run command:
`./gradlew shadowJar`

This will build a jar in folder `/build/libs` with all dependencies and resources in it.


## 📜 License

This project is licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
(CC BY-NC-SA 4.0)** license.  
See the full text here: [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)

### ✅ What you can do

| Action            | Allowed? | Notes                                                       |
|-------------------|----------|-------------------------------------------------------------|
| ✔️ Use / Copy     | Yes      | You may use and share the work freely **with attribution**. |
| ✔️ Modify / Adapt | Yes      | You may remix, transform, or build upon the work.           |
| ✔️ Redistribute   | Yes      | Redistribution is allowed under the **same license**.       |

### ❌ What you cannot do

| Action                             | Allowed? | Notes                                                       |
|------------------------------------|----------|-------------------------------------------------------------|
| ❌ Commercial Use                   | No       | You may not use this work for **commercial purposes**.      |
| ❌ Re-license under different terms | No       | Derivative works must use the **same CC BY-NC-SA license**. |

## Third-Party Licenses

- [JAMA](http://math.nist.gov/javanumerics/jama/)
  Licensed under CC0 1.0 Public Domain.
  Copyright Notice: This software is a cooperative product of The MathWorks and the
  National Institute of Standards and Technology (NIST) which has been released to the
  public domain. Neither The MathWorks nor NIST assumes any responsibility whatsoever
  for its use by other parties, and makes no guarantees, expressed or implied, about its
  quality, reliability, or any other characteristic.

- [big-math](https://github.com/eobermuhlner/big-math)
  Licensed under MIT License.
  Copyright (c) 2017 Eric Obermühlner.
  See full license in [LICENSES/big-math/LICENSE.txt](./LICENSES/big-math/LICENSE.txt).

- [Kotlin](https://github.com/JetBrains/kotlin)
  Licensed under Apache License 2.0.
  Copyright 2010-2024 JetBrains s.r.o and respective authors and developers.
  See full license in [LICENSES/kotlin/LICENSE.txt](./LICENSES/kotlin/LICENSE.txt).

- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
  Licensed under Apache License 2.0.
  Copyright 2017-2019 JetBrains s.r.o and respective authors and developers.
  See full license in [LICENSES/kotlinx-serialization/LICENSE.txt](./LICENSES/kotlinx-serialization/LICENSE.txt).

- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)
  Licensed under Apache License 2.0.
  Copyright 2016-2025 JetBrains s.r.o and contributors.
  See full license in [LICENSES/kotlinx-coroutines/LICENSE.txt](./LICENSES/kotlinx-coroutines/LICENSE.txt).

## Acknowledgement

This project was inspired by [CatMAP](https://github.com/SUNCAT-Center/catmap).
While the overall program structure and design ideas were influenced by this project.
All implementation in this repository was written independently.
