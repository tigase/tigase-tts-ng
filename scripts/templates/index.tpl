yieldUnescaped '<!DOCTYPE html>'
html {
    head {
        title('Tigase TTS-NG test summary')
        meta(charset: "utf-8")

        newLine()
        link(rel: "stylesheet", href: "assets/css/bootstrap.min.css")
        newLine()
        link(rel: "stylesheet", href: "assets/css/custom.css")
        newLine()
        link(rel: "stylesheet", href: "assets/fonts/fonts.css")
    }
    body {

        div(class: "wrapper") {
            nav(class: "navbar navbar-default navbar-static-top", role:"navigation") {
                div(class: "navbar-header") {
                    a(class: "navbar-brand", href: "index.html", title: "Home", id: "logo") {
                        p("Tigase XMPP Server: TTS-NG reports")
                    }
                }
            }

            div(id: "page-wrapper", class: "no-sidebar") {
                div(class: "row") {

                    div(class: "well well-lg") {
                        yield 'This is a page with reports for the automatic TTS-NG tests we run for each nightly release'
                        br() br()
                        yield 'Following tests are available'
                        br() br()

                        div (class: "list-group", style: "margin-bottom: 0px") {
                            ((Map)tests).each {testType,versionsForTestType ->
                                a(class: "list-group-item", href: "#"+testType, testType == 'all' ? 'All available tests' : testType)
                            }
                        }
                    }

                    ((Map)tests).each {testType, versionsForTestType ->

                        div(class: "table-responsive") {
                            table (class: "table table-striped table-bordered", summary:"Tigase XMPP Server tests results for different databases") {
                                caption(id: testType, testType == 'all' ? 'All available tests' : testType)

                                thead() {
                                    tr() {
                                        th("Date")
                                        th("Version")
                                        testAllDBs[testType].each { dbs ->
                                            th(style: "width: calc((100%-300px)/${testAllDBs[testType].size()});", dbs)
                                        }
                                    }
                                }

                                tbody() {
                                    ((Map)versionsForTestType.descendingMap()).each {versionNumber,versionDBs ->
                                        tr {
                                            td(versionDBs?.values()*.finishedDate?.max() ?: "--")

                                            td(versionNumber)

                                            testAllDBs[testType].each { dbs ->
                                                if (versionDBs[dbs]) {
                                                    td(class: versionDBs[dbs]?.getTestResult().getCssClass()) {

                                                        a(href: versionDBs[dbs]?.getReportFile()) {
                                                            if (versionDBs[dbs]?.getMetricsAsString() ) {
                                                                div(class: "tooltip2") {
                                                                    span(class: "tooltiptext2", "Passed / Failed | Total")
                                                                    yield versionDBs[dbs]?.getMetricsAsString()
                                                                }
                                                            }
                                                            if (versionDBs[dbs]?.getDuration()) {
                                                                div(class: "tooltip2") {
                                                                    span(class: "tooltiptext2", "Test running time")
                                                                    yield  versionDBs[dbs]?.getDuration()
                                                                }
                                                            }
                                                        }
                                                        if (versionDBs[dbs]?.getLogFile() != null ) {
                                                            if(versionDBs[dbs]?.getReportFile()) br()
                                                            div(class: "tooltip2") {
                                                                span(class: "tooltiptext2", "Server log: logs/tigase-console.log")
                                                                a(href: versionDBs[dbs]?.getLogFile(), "console.log")
                                                            }
                                                        }

                                                    }
                                                } else {
                                                    td("-")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
