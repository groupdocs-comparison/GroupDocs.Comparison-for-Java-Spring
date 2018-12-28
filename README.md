# GroupDocs.Comparison-for-Java-Spring Example
New GroupDocs.Comparison for Java Spring UI Example
###### version 1.9.1

[![Build Status](https://travis-ci.org/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring.svg?branch=master)](https://travis-ci.org/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring)
[![Maintainability](https://api.codeclimate.com/v1/badges/f945b8bc09a5ff2e8a2d/maintainability)](https://codeclimate.com/github/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring/maintainability)

## System Requirements
- Java 8 (JDK 1.8)
- Maven 3


## Description
GroupDocs.Comparison is a native, simple, fully configurable and optimized web application which allows you to manipulate documents without requiring any other commercial application through GroupDocs APIs.

**Note** Without a license application will run in trial mode, purchase [GroupDocs.Comparison for Java license](https://purchase.groupdocs.com/order-online-step-1-of-8.aspx) or request [GroupDocs.Comparison for Java temporary license](https://purchase.groupdocs.com/temporary-license).


## Demo Video
Coming soon


## Features
#### GroupDocs.Comparison
- Clean, modern and intuitive design
- Easily switchable colour theme (create your own colour theme in 5 minutes)
- Responsive design
- Mobile support (open application on any mobile device)
- HTML and image modes
- Fully customizable navigation panel
- Compare documents
- Multi-compare several documents
- Compare password protected documents
- Upload documents
- Display clearly visible differences
- Download comparison results
- Print comparison results
- Smooth document scrolling
- Preload pages for faster document rendering
- Multi-language support for displaying errors
- Cross-browser support (Safari, Chrome, Opera, Firefox)
- Cross-platform support (Windows, Linux, MacOS)


## How to run

You can run this sample by one of following methods


#### Build from source

Download [source code](https://github.com/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring/archive/master.zip) from github or clone this repository.

```bash
git clone https://github.com/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring
cd GroupDocs.Comparison-for-Java-Spring
mvn clean spring-boot:run
## Open http://localhost:8080/comparison/ in your favorite browser.
```

#### Binary release (with all dependencies)

Download [latest release](https://github.com/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring/releases/latest) from [releases page](https://github.com/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring/releases). 

**Note**: This method is **recommended** for running this sample behind firewall.

```bash
curl -J -L -o release.tar.gz https://github.com/groupdocs-comparison/GroupDocs.Comparison-for-Java-Spring/releases/download/1.9.1/release.tar.gz
tar -xvzf release.tar.gz
cd release
java -jar comparison-spring-1.9.1.jar configuration.yaml
## Open http://localhost:8080/comparison/ in your favorite browser.
```

#### Docker image
Use [docker](https://www.docker.com/) image.

```bash
mkdir DocumentSamples
mkdir Licenses
docker run -p 8080:8080 -v `pwd`/DocumentSamples:/home/groupdocs/app/DocumentSamples -v `pwd`/Licenses:/home/groupdocs/app/Licenses groupdocs/comparison-for-java-spring
## Open http://localhost:8080/comparison/ in your favorite browser.
```

#### Configuration
For all methods above you can adjust settings in `configuration.yml`. By default in this sample will lookup for license file in `./Licenses` folder, so you can simply put your license file in that folder or specify relative/absolute path by setting `licensePath` value in `configuration.yml`. 

## Resources
- **Website:** [www.groupdocs.com](http://www.groupdocs.com)
- **Product Home:** [GroupDocs.Comparison for Java](https://products.groupdocs.com/comparison/java)
- **Product API References:** [GroupDocs.Comparison for Java API](https://apireference.groupdocs.com)
- **Download:** [Download GroupDocs.Comparison for Java](http://downloads.groupdocs.com/comparison/java)
- **Documentation:** [GroupDocs.Comparison for Java Documentation](https://docs.groupdocs.com/dashboard.action)
- **Free Support Forum:** [GroupDocs.Comparison for Java Free Support Forum](https://forum.groupdocs.com/c/comparison)
- **Paid Support Helpdesk:** [GroupDocs.Comparison for Java Paid Support Helpdesk](https://helpdesk.groupdocs.com)
- **Blog:** [GroupDocs.Comparison for Java Blog](https://blog.groupdocs.com/category/groupdocs-comparison-product-family)