package dev.craftstudio.testfrontend

import dev.craftstudio.auth.authenticateUser
import dev.craftstudio.data.developer.GetAvailableCommissionsResponse
import dev.craftstudio.utils.httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.html.*

fun Application.configureTestFrontend() {
    routing {
        authenticateUser { accountGetter ->
            get("/frontend/developer/jobs") {
                val account = accountGetter()

                if (!account.isDeveloper)
                    return@get call.respondRedirect("http://localhost:8080/signup/developer")

                val page = call.parameters["page"]?.toIntOrNull() ?: 1

                val commissions = httpClient.get("http://localhost:8080/developer/available-commissions?page=$page") {
                    bearerAuth(account.accessToken)
                }.body<GetAvailableCommissionsResponse>().commissions

                call.respondHtml {
                    head {
                        title { +"CraftStudio" }
                    }

                    body {
                        h1 { +"Available Jobs" }

                        ul {
                            commissions.forEach {
                                li {
                                    a(href = "http://localhost:8080/frontend/developer/jobs/${it.commissionId}") { +it.title }
                                }
                            }
                        }
                    }
                }

            }

            get("/frontend/buyer/submit-job") {
                val account = accountGetter()
                val buyerAccount = account.buyerAccount?.resolve()
                    ?: return@get call.respondRedirect("http://localhost:8080/signup/buyer")

                call.respondHtml {
                    head {
                        title { +"CraftStudio" }

                        link(rel = "stylesheet", href = "/styles.css")
                    }

                    body {
                        h1 { +"Submit Job" }

                        script {
                            +"""
                                function getCookie(name) {
                                  const value = `; ${"\${document.cookie}"}`;
                                  const parts = value.split(`; ${"\${name}"}=`);
                                  if (parts.length === 2) return parts.pop().split(';').shift();
                                }
                            """.trimIndent()
                        }

                        if (buyerAccount.remainingCommissions == 0) { // -1 means unlimited
                            p { +"You have no remaining commissions" }
                        } else {
                            div(classes = "form") {
                                div {
                                    label { +"Title" }
                                    input(InputType.text, name = "title")
                                }
                                div {
                                    label { +"Summary" }
                                    input(InputType.text, name = "summary")
                                }
                                div {
                                    label { +"Requirements" }
                                    input(InputType.text, name = "requirements")
                                }
                                div {
                                    label { +"Fixed Price Amount" }
                                    input(InputType.number, name = "fixedPriceAmount")
                                }
                                div {
                                    label { +"Hourly Price Amount" }
                                    input(InputType.number, name = "hourlyPriceAmount")
                                }
                                div {
                                    label { +"Expiry Days" }
                                    input(InputType.number, name = "expiryDays")
                                }
                                div {
                                    label { +"Minimum Reputation" }
                                    input(InputType.number, name = "minimumReputation")
                                }
                                div {
                                    button {
                                        onClick = """
                                            let request = {
                                                title: document.querySelector('input[name=title]').value,
                                               summary: document.querySelector('input[name=summary]').value,
                                               requirements: document.querySelector('input[name=requirements]').value,
                                               fixedPriceAmount: document.querySelector('input[name=fixedPriceAmount]').value,
                                               hourlyPriceAmount: document.querySelector('input[name=hourlyPriceAmount]').value,
                                               expiryDays: document.querySelector('input[name=expiryDays]').value,
                                               minimumReputation: document.querySelector('input[name=minimumReputation]').value
                                            }
                                            
                                            let user_session = JSONgetCookie("user_session")
                                            console.log(user_session)
                                        """.trimIndent()
                                        +"Submit"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        get("/styles.css") {
            call.respondText(CssBuilder().apply {
                rule(".form") {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                }
            }.toString())
        }
    }
}