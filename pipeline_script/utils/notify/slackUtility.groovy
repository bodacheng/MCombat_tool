
// スタイルデータ
STYLE_DATA = [
                    "Android_Dev" : [
                        "BOT_ICON" : ":droid:",
                        "SHORT_NAME" : "Android Dev",
                        "NAME" : "Android Dev",
                        "BAR_COLOR_1" : "A3C63E",
                        "BAR_COLOR_2" : "ddf29d",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/droid/5636dcbfb58c7823.png"
                    ],
                    "Android_QA" : [
                        "BOT_ICON" : ":droid:",
                        "SHORT_NAME" : "Android QA",
                        "NAME" : "Android QA",
                        "BAR_COLOR_1" : "A3C63E",
                        "BAR_COLOR_2" : "ddf29d",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/droid/5636dcbfb58c7823.png"
                    ],
                	"Android_Beta" : [
                		"BOT_ICON" : ":droid:",
                		"SHORT_NAME" : "Android Beta",
                		"NAME" : "Android Beta",
                		"BAR_COLOR_1" : "A3C63E",
                		"BAR_COLOR_2" : "ddf29d",
                		"ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/droid/5636dcbfb58c7823.png"
                	],
                    "Android_Release" : [
                        "BOT_ICON" : ":droid:",
                        "SHORT_NAME" : "Android 本番",
                        "NAME" : "Android 本番",
                        "BAR_COLOR_1" : "A3C63E",
                        "BAR_COLOR_2" : "ddf29d",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/droid/5636dcbfb58c7823.png"
                    ],
                    "iOS_Dev" : [
                        "BOT_ICON" : ":apple3:",
                        "SHORT_NAME" : "iOS Dev",
                        "NAME" : "iOS Dev",
                        "BAR_COLOR_1" : "#ffffff",
                        "BAR_COLOR_2" : "#d4d9d8",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "iOS_QA" : [
                        "BOT_ICON" : ":apple3:",
                        "SHORT_NAME" : "iOS QA",
                        "NAME" : "iOS QA",
                        "BAR_COLOR_1" : "#ffffff",
                        "BAR_COLOR_2" : "#d4d9d8",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "iOS_Beta" : [
                        "BOT_ICON" : ":apple3:",
                        "SHORT_NAME" : "iOS Beta",
                        "NAME" : "iOS Beta",
                        "BAR_COLOR_1" : "#ffffff",
                        "BAR_COLOR_2" : "#d4d9d8",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "iOS_Release" : [
                        "BOT_ICON" : ":apple3:",
                        "SHORT_NAME" : "iOS 本番",
                        "NAME" : "iOS 本番",
                        "BAR_COLOR_1" : "#ffffff",
                        "BAR_COLOR_2" : "#d4d9d8",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "Asset_Dev" : [
                        "BOT_ICON" : ":asset:",
                        "SHORT_NAME" : "Asset Dev|QA",
                        "NAME" : "Asset Dev|QA",
                        "BAR_COLOR_1" : "#49829e",
                        "BAR_COLOR_2" : "#a8d8f0",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "Asset_Test" : [
                        "BOT_ICON" : ":asset:",
                        "SHORT_NAME" : "Asset Test",
                        "NAME" : "Asset Test",
                        "BAR_COLOR_1" : "#49829e",
                        "BAR_COLOR_2" : "#a8d8f0",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "Asset_Beta" : [
                        "BOT_ICON" : ":asset:",
                        "SHORT_NAME" : "Asset Beta",
                        "NAME" : "Asset Beta",
                        "BAR_COLOR_1" : "#49829e",
                        "BAR_COLOR_2" : "#a8d8f0",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
                    "Asset_Release" : [
                        "BOT_ICON" : ":asset:",
                        "SHORT_NAME" : "Asset 本番",
                        "NAME" : "Asset 本番",
                        "BAR_COLOR_1" : "#49829e",
                        "BAR_COLOR_2" : "#a8d8f0",
                        "ICON_1" : "https://emoji.slack-edge.com/T02BEABAP/apple2/d1fb273e31a23336.png"
                    ],
            	]

/**
 * Slack通知を行う（bot/attachments利用)
 * @param platform iOS or Android
 */
void notifySlackSendMessage(SlackNotify slackNotify) {
    def toMessageTarget = slackNotify.platform + "_" + slackNotify.buildKind
    def style = STYLE_DATA[toMessageTarget]
    println "send message for Platform" + toMessageTarget
    def attachments = [
                        [
                            "fallback": style["SHORT_NAME"] + "のダウロードが可能になりました",
                            "color": style["BAR_COLOR_1"],
                            "author_name": style["NAME"],
                            "author_icon": style["ICON_1"],
                            "title": "Click here to Download",
                            "text" : slackNotify.releaseNote,
                            "title_link": slackNotify.downloadURL,
                        ],
                        [
                            "color": style["BAR_COLOR_2"],
                            "fields": [
                                [
                                    "title": "Branch",
                                    "value": slackNotify.branch,
                                    "short": false
                                ],
                                [
                                    "title": "Hash",
                                    "value": slackNotify.hash,
                                    "short": true
                                ],
                                [
                                    "title": "Version",
                                    "value": slackNotify.version,
                                    "short": true
                                ],
                                [
                                    "title": "BuildKind",
                                    "value": slackNotify.buildKind,
                                    "short": true
                                ],
                                [
                                    "title": "AssetKind",
                                    "value": slackNotify.assetKind,
                                    "short": true
                                ],
                                [
                                    "title": "Release ID",
                                    "value": slackNotify.appcenterRleaseId,
                                    "short": true
                                ],
                                [
                                    "title": "Builder",
                                    "value": slackNotify.buildUser,
                                    "short": true
                                ],
                                [
                                    "title": "ビルド時間",
                                    "value": slackNotify.buildTime,
                                    "short": true
                                ]
                            ],

                            "footer": style["SHORT_NAME"],
                            "footer_icon": style["ICON_1"],
                            "ts": "hogehogetime"
                        ]
                    ]

    slackSend channel:slackNotify.channels,
    teamDomain: env.SLACK_DOMAIN,
    tokenCredentialId: slackNotify.credentialsId,
    botUser: true,
    iconEmoji: style["BOT_ICON"],
    username: slackNotify.botUserName,
    attachments: attachments
}

/**
 * アセットジョブを走らせた場合の成功通知
 * @param slackNotify  [description]
 */
void notifySlackSendMessageForAsset(SlackNotify slackNotify) {
    def toMessageTarget =  "Asset_" + slackNotify.assetKind
    def style = STYLE_DATA[toMessageTarget]
    println "send message for Asset " + toMessageTarget

    def attachments = [
                        [
                            "fallback": style["SHORT_NAME"] + "のビルドが完了しました。",
                            "color": style["BAR_COLOR_1"],
                            "author_name": style["NAME"],
                            "author_icon": style["ICON_1"],
                            "title": "Success Upload Asset",
                            "text" : slackNotify.releaseNote,
                        ],
                        [
                            "color": style["BAR_COLOR_2"],
                            "fields": [
                                [
                                    "title": "Branch",
                                    "value": slackNotify.branch,
                                    "short": false
                                ],
                                [
                                    "title": "Hash",
                                    "value": slackNotify.hash,
                                    "short": true
                                ],
                                [
                                    "title": "Target",
                                    "value": slackNotify.assetKind,
                                    "short": true
                                ],
                                [
                                    "title": "Builder",
                                    "value": slackNotify.buildUser,
                                    "short": true
                                ],
                                [
                                    "title": "ビルド時間",
                                    "value": slackNotify.buildTime,
                                    "short": true
                                ]
                            ],

                            "footer": style["SHORT_NAME"],
                            "footer_icon": style["ICON_1"],
                            "ts": "hogehogetime"
                        ]
                    ]

    slackSend channel:slackNotify.channels,
    teamDomain: env.SLACK_DOMAIN,
    tokenCredentialId: slackNotify.credentialsId,
    botUser: true,
    iconEmoji: style["BOT_ICON"],
    username: slackNotify.botUserName,
    attachments: attachments
}

/**
 * Jobを叩いたタイミングで開始通知を出す
 * @param slackNotify  [description]
 */
void notifyStartSlackSendMessage(SlackNotify slackNotify) {
    def toMessageTarget = slackNotify.platform + "_" + slackNotify.buildKind
    def style = STYLE_DATA[toMessageTarget]
    println "send message for Platform" + toMessageTarget
    def attachments = [
                        [
                            "fallback": style["SHORT_NAME"] + "のジョブを開始します。",
                            "color": style["BAR_COLOR_1"],
                            "author_name": style["NAME"],
                            "author_icon": style["ICON_1"],
                            "text" : slackNotify.releaseNote,
                        ],
                        [
                            "color": style["BAR_COLOR_2"],
                            "fields": [
                                [
                                    "title": "Branch",
                                    "value": slackNotify.branch,
                                    "short": false
                                ],
                                [
                                    "title": "Target",
                                    "value": slackNotify.buildKind,
                                    "short": true
                                ],
                                [
                                    "title": "Builder",
                                    "value": slackNotify.buildUser,
                                    "short": true
                                ]
                            ],

                            "footer": style["SHORT_NAME"],
                            "footer_icon": style["ICON_1"],
                            "ts": "hogehogetime"
                        ]
                    ]

    slackSend channel:slackNotify.channels,
    teamDomain: env.SLACK_DOMAIN,
    tokenCredentialId: slackNotify.credentialsId,
    botUser: true,
    iconEmoji: style["BOT_ICON"],
    username: slackNotify.botUserName,
    attachments: attachments
}

/**
 * Jobを叩いたタイミングで開始通知を出す(Assetビルドの通知)
 * @param slackNotify  [description]
 */
void notifyStartSlackSendMessageAsset(SlackNotify slackNotify) {
    def toMessageTarget = "Asset_" + slackNotify.assetKind
    def style = STYLE_DATA[toMessageTarget]
    println "send message for Asset" + toMessageTarget
    def attachments = [
                        [
                            "fallback": style["SHORT_NAME"] + "のジョブを開始します。",
                            "color": style["BAR_COLOR_1"],
                            "author_name": style["NAME"],
                            "author_icon": style["ICON_1"],
                            "text" : slackNotify.releaseNote,
                        ],
                        [
                            "color": style["BAR_COLOR_2"],
                            "fields": [
                                [
                                    "title": "Branch",
                                    "value": slackNotify.branch,
                                    "short": false
                                ],
                                [
                                    "title": "Target",
                                    "value": slackNotify.assetKind,
                                    "short": true
                                ],
                                [
                                    "title": "Builder",
                                    "value": slackNotify.buildUser,
                                    "short": true
                                ]
                            ],

                            "footer": style["SHORT_NAME"],
                            "footer_icon": style["ICON_1"],
                            "ts": "hogehogetime"
                        ]
                    ]

    slackSend channel:slackNotify.channels,
    teamDomain: env.SLACK_DOMAIN,
    tokenCredentialId: slackNotify.credentialsId,
    botUser: true,
    iconEmoji: style["BOT_ICON"],
    username: slackNotify.botUserName,
    attachments: attachments
}

return this
