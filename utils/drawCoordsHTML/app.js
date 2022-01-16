var canvas = document.getElementById('canvas');
var ctx = canvas.getContext("2d");

ctx.fillStyle = "#000000";

const data = [
    {
        "x": 619,
        "y": 657,
        "index": 0
    },
    {
        "x": 634,
        "y": 645,
        "index": 1
    },
    {
        "x": 649,
        "y": 632,
        "index": 2
    },
    {
        "x": 663,
        "y": 619,
        "index": 3
    },
    {
        "x": 685,
        "y": 604,
        "index": 4
    },
    {
        "x": 703,
        "y": 596,
        "index": 5
    },
    {
        "x": 722,
        "y": 588,
        "index": 6
    },
    {
        "x": 744,
        "y": 579,
        "index": 7
    },
    {
        "x": 762,
        "y": 575,
        "index": 8
    },
    {
        "x": 775,
        "y": 556,
        "index": 9
    },
    {
        "x": 774,
        "y": 534,
        "index": 10
    },
    {
        "x": 777,
        "y": 510,
        "index": 11
    },
    {
        "x": 786,
        "y": 492,
        "index": 12
    },
    {
        "x": 802,
        "y": 470,
        "index": 13
    },
    {
        "x": 815,
        "y": 456,
        "index": 14
    },
    {
        "x": 828,
        "y": 443,
        "index": 15
    },
    {
        "x": 833,
        "y": 415,
        "index": 16
    },
    {
        "x": 846,
        "y": 394,
        "index": 17
    },
    {
        "x": 853,
        "y": 375,
        "index": 18
    },
    {
        "x": 845,
        "y": 356,
        "index": 19
    },
    {
        "x": 836,
        "y": 339,
        "index": 20
    },
    {
        "x": 832,
        "y": 313,
        "index": 21
    },
    {
        "x": 839,
        "y": 293,
        "index": 22
    },
    {
        "x": 846,
        "y": 275,
        "index": 23
    },
    {
        "x": 852,
        "y": 247,
        "index": 24
    },
    {
        "x": 851,
        "y": 227,
        "index": 25
    },
    {
        "x": 850,
        "y": 209,
        "index": 26
    },
    {
        "x": 850,
        "y": 189,
        "index": 27
    },
    {
        "x": 850,
        "y": 170,
        "index": 28
    },
    {
        "x": 845,
        "y": 145,
        "index": 29
    },
    {
        "x": 842,
        "y": 128,
        "index": 30
    },
    {
        "x": 838,
        "y": 108,
        "index": 31
    },
    {
        "x": 835,
        "y": 91,
        "index": 32
    },
    {
        "x": 832,
        "y": 72,
        "index": 33
    },
    {
        "x": 812,
        "y": 57,
        "index": 34
    },
    {
        "x": 789,
        "y": 61,
        "index": 35
    },
    {
        "x": 774,
        "y": 75,
        "index": 36
    },
    {
        "x": 761,
        "y": 90,
        "index": 37
    },
    {
        "x": 749,
        "y": 100,
        "index": 38
    },
    {
        "x": 735,
        "y": 114,
        "index": 39
    },
    {
        "x": 721,
        "y": 126,
        "index": 40
    },
    {
        "x": 708,
        "y": 139,
        "index": 41
    },
    {
        "x": 691,
        "y": 152,
        "index": 42
    },
    {
        "x": 667,
        "y": 162,
        "index": 43
    },
    {
        "x": 649,
        "y": 164,
        "index": 44
    },
    {
        "x": 630,
        "y": 171,
        "index": 45
    },
    {
        "x": 607,
        "y": 171,
        "index": 46
    },
    {
        "x": 588,
        "y": 167,
        "index": 47
    },
    {
        "x": 568,
        "y": 163,
        "index": 48
    },
    {
        "x": 542,
        "y": 153,
        "index": 49
    },
    {
        "x": 531,
        "y": 142,
        "index": 50
    },
    {
        "x": 518,
        "y": 128,
        "index": 51
    },
    {
        "x": 504,
        "y": 116,
        "index": 52
    },
    {
        "x": 492,
        "y": 105,
        "index": 53
    },
    {
        "x": 479,
        "y": 93,
        "index": 54
    },
    {
        "x": 467,
        "y": 83,
        "index": 55
    },
    {
        "x": 454,
        "y": 69,
        "index": 56
    },
    {
        "x": 433,
        "y": 70,
        "index": 57
    },
    {
        "x": 420,
        "y": 87,
        "index": 58
    },
    {
        "x": 417,
        "y": 106,
        "index": 59
    },
    {
        "x": 414,
        "y": 123,
        "index": 60
    },
    {
        "x": 410,
        "y": 141,
        "index": 61
    },
    {
        "x": 407,
        "y": 157,
        "index": 62
    },
    {
        "x": 404,
        "y": 178,
        "index": 63
    },
    {
        "x": 402,
        "y": 196,
        "index": 64
    },
    {
        "x": 400,
        "y": 214,
        "index": 65
    },
    {
        "x": 400,
        "y": 234,
        "index": 66
    },
    {
        "x": 398,
        "y": 252,
        "index": 67
    },
    {
        "x": 403,
        "y": 272,
        "index": 68
    },
    {
        "x": 411,
        "y": 293,
        "index": 69
    },
    {
        "x": 413,
        "y": 307,
        "index": 70
    },
    {
        "x": 409,
        "y": 329,
        "index": 71
    },
    {
        "x": 402,
        "y": 347,
        "index": 72
    },
    {
        "x": 395,
        "y": 364,
        "index": 73
    },
    {
        "x": 393,
        "y": 387,
        "index": 74
    },
    {
        "x": 410,
        "y": 409,
        "index": 75
    },
    {
        "x": 415,
        "y": 437,
        "index": 76
    },
    {
        "x": 427,
        "y": 450,
        "index": 77
    },
    {
        "x": 439,
        "y": 465,
        "index": 78
    },
    {
        "x": 452,
        "y": 484,
        "index": 79
    },
    {
        "x": 457,
        "y": 503,
        "index": 80
    },
    {
        "x": 460,
        "y": 531,
        "index": 81
    },
    {
        "x": 458,
        "y": 552,
        "index": 82
    },
    {
        "x": 469,
        "y": 569,
        "index": 83
    },
    {
        "x": 489,
        "y": 575,
        "index": 84
    },
    {
        "x": 512,
        "y": 586,
        "index": 85
    },
    {
        "x": 529,
        "y": 595,
        "index": 86
    },
    {
        "x": 546,
        "y": 603,
        "index": 87
    },
    {
        "x": 568,
        "y": 620,
        "index": 88
    },
    {
        "x": 584,
        "y": 635,
        "index": 89
    },
    {
        "x": 597,
        "y": 648,
        "index": 90
    },
    {
        "x": 613,
        "y": 634,
        "index": 91
    },
    {
        "x": 634,
        "y": 605,
        "index": 92
    },
    {
        "x": 650,
        "y": 598,
        "index": 93
    },
    {
        "x": 675,
        "y": 584,
        "index": 94
    },
    {
        "x": 679,
        "y": 565,
        "index": 95
    },
    {
        "x": 684,
        "y": 544,
        "index": 96
    },
    {
        "x": 689,
        "y": 525,
        "index": 97
    },
    {
        "x": 693,
        "y": 507,
        "index": 98
    },
    {
        "x": 718,
        "y": 508,
        "index": 99
    },
    {
        "x": 734,
        "y": 524,
        "index": 100
    },
    {
        "x": 746,
        "y": 537,
        "index": 101
    },
    {
        "x": 759,
        "y": 552,
        "index": 102
    },
    {
        "x": 718,
        "y": 480,
        "index": 103
    },
    {
        "x": 735,
        "y": 468,
        "index": 104
    },
    {
        "x": 751,
        "y": 457,
        "index": 105
    },
    {
        "x": 767,
        "y": 446,
        "index": 106
    },
    {
        "x": 789,
        "y": 440,
        "index": 107
    },
    {
        "x": 809,
        "y": 440,
        "index": 108
    },
    {
        "x": 697,
        "y": 470,
        "index": 109
    },
    {
        "x": 696,
        "y": 450,
        "index": 110
    },
    {
        "x": 695,
        "y": 428,
        "index": 111
    },
    {
        "x": 703,
        "y": 410,
        "index": 112
    },
    {
        "x": 718,
        "y": 399,
        "index": 113
    },
    {
        "x": 736,
        "y": 385,
        "index": 114
    },
    {
        "x": 752,
        "y": 373,
        "index": 115
    },
    {
        "x": 766,
        "y": 363,
        "index": 116
    },
    {
        "x": 783,
        "y": 351,
        "index": 117
    },
    {
        "x": 799,
        "y": 338,
        "index": 118
    },
    {
        "x": 814,
        "y": 328,
        "index": 119
    },
    {
        "x": 835,
        "y": 177,
        "index": 120
    },
    {
        "x": 818,
        "y": 186,
        "index": 121
    },
    {
        "x": 800,
        "y": 196,
        "index": 122
    },
    {
        "x": 784,
        "y": 203,
        "index": 123
    },
    {
        "x": 762,
        "y": 216,
        "index": 124
    },
    {
        "x": 747,
        "y": 229,
        "index": 125
    },
    {
        "x": 732,
        "y": 243,
        "index": 126
    },
    {
        "x": 713,
        "y": 268,
        "index": 127
    },
    {
        "x": 708,
        "y": 285,
        "index": 128
    },
    {
        "x": 701,
        "y": 305,
        "index": 129
    },
    {
        "x": 696,
        "y": 321,
        "index": 130
    },
    {
        "x": 689,
        "y": 342,
        "index": 131
    },
    {
        "x": 683,
        "y": 359,
        "index": 132
    },
    {
        "x": 677,
        "y": 377,
        "index": 133
    },
    {
        "x": 670,
        "y": 397,
        "index": 134
    },
    {
        "x": 670,
        "y": 426,
        "index": 135
    },
    {
        "x": 676,
        "y": 444,
        "index": 136
    },
    {
        "x": 701,
        "y": 357,
        "index": 137
    },
    {
        "x": 720,
        "y": 359,
        "index": 138
    },
    {
        "x": 742,
        "y": 362,
        "index": 139
    },
    {
        "x": 697,
        "y": 488,
        "index": 140
    },
    {
        "x": 683,
        "y": 475,
        "index": 141
    },
    {
        "x": 669,
        "y": 460,
        "index": 142
    },
    {
        "x": 654,
        "y": 445,
        "index": 143
    },
    {
        "x": 642,
        "y": 432,
        "index": 144
    },
    {
        "x": 628,
        "y": 419,
        "index": 145
    },
    {
        "x": 630,
        "y": 395,
        "index": 146
    },
    {
        "x": 636,
        "y": 379,
        "index": 147
    },
    {
        "x": 643,
        "y": 360,
        "index": 148
    },
    {
        "x": 649,
        "y": 340,
        "index": 149
    },
    {
        "x": 656,
        "y": 322,
        "index": 150
    },
    {
        "x": 662,
        "y": 304,
        "index": 151
    },
    {
        "x": 676,
        "y": 293,
        "index": 152
    },
    {
        "x": 691,
        "y": 277,
        "index": 153
    },
    {
        "x": 705,
        "y": 264,
        "index": 154
    },
    {
        "x": 722,
        "y": 238,
        "index": 155
    },
    {
        "x": 730,
        "y": 222,
        "index": 156
    },
    {
        "x": 739,
        "y": 206,
        "index": 157
    },
    {
        "x": 750,
        "y": 189,
        "index": 158
    },
    {
        "x": 759,
        "y": 172,
        "index": 159
    },
    {
        "x": 768,
        "y": 156,
        "index": 160
    },
    {
        "x": 778,
        "y": 140,
        "index": 161
    },
    {
        "x": 787,
        "y": 124,
        "index": 162
    },
    {
        "x": 796,
        "y": 108,
        "index": 163
    },
    {
        "x": 813,
        "y": 86,
        "index": 164
    },
    {
        "x": 714,
        "y": 224,
        "index": 165
    },
    {
        "x": 708,
        "y": 206,
        "index": 166
    },
    {
        "x": 704,
        "y": 192,
        "index": 167
    },
    {
        "x": 699,
        "y": 171,
        "index": 168
    },
    {
        "x": 620,
        "y": 186,
        "index": 169
    },
    {
        "x": 620,
        "y": 204,
        "index": 170
    },
    {
        "x": 620,
        "y": 224,
        "index": 171
    },
    {
        "x": 630,
        "y": 250,
        "index": 172
    },
    {
        "x": 641,
        "y": 266,
        "index": 173
    },
    {
        "x": 652,
        "y": 282,
        "index": 174
    },
    {
        "x": 610,
        "y": 248,
        "index": 175
    },
    {
        "x": 597,
        "y": 264,
        "index": 176
    },
    {
        "x": 587,
        "y": 279,
        "index": 177
    },
    {
        "x": 578,
        "y": 306,
        "index": 178
    },
    {
        "x": 585,
        "y": 325,
        "index": 179
    },
    {
        "x": 589,
        "y": 343,
        "index": 180
    },
    {
        "x": 596,
        "y": 364,
        "index": 181
    },
    {
        "x": 600,
        "y": 381,
        "index": 182
    },
    {
        "x": 606,
        "y": 400,
        "index": 183
    },
    {
        "x": 613,
        "y": 432,
        "index": 184
    },
    {
        "x": 613,
        "y": 451,
        "index": 185
    },
    {
        "x": 613,
        "y": 472,
        "index": 186
    },
    {
        "x": 613,
        "y": 490,
        "index": 187
    },
    {
        "x": 614,
        "y": 512,
        "index": 188
    },
    {
        "x": 614,
        "y": 531,
        "index": 189
    },
    {
        "x": 614,
        "y": 551,
        "index": 190
    },
    {
        "x": 614,
        "y": 573,
        "index": 191
    },
    {
        "x": 615,
        "y": 591,
        "index": 192
    },
    {
        "x": 659,
        "y": 543,
        "index": 193
    },
    {
        "x": 628,
        "y": 527,
        "index": 194
    },
    {
        "x": 590,
        "y": 528,
        "index": 195
    },
    {
        "x": 567,
        "y": 544,
        "index": 196
    },
    {
        "x": 591,
        "y": 603,
        "index": 197
    },
    {
        "x": 573,
        "y": 593,
        "index": 198
    },
    {
        "x": 555,
        "y": 582,
        "index": 199
    },
    {
        "x": 551,
        "y": 561,
        "index": 200
    },
    {
        "x": 547,
        "y": 541,
        "index": 201
    },
    {
        "x": 544,
        "y": 522,
        "index": 202
    },
    {
        "x": 540,
        "y": 502,
        "index": 203
    },
    {
        "x": 471,
        "y": 547,
        "index": 204
    },
    {
        "x": 486,
        "y": 532,
        "index": 205
    },
    {
        "x": 499,
        "y": 520,
        "index": 206
    },
    {
        "x": 512,
        "y": 507,
        "index": 207
    },
    {
        "x": 528,
        "y": 494,
        "index": 208
    },
    {
        "x": 544,
        "y": 478,
        "index": 209
    },
    {
        "x": 557,
        "y": 467,
        "index": 210
    },
    {
        "x": 571,
        "y": 452,
        "index": 211
    },
    {
        "x": 586,
        "y": 438,
        "index": 212
    },
    {
        "x": 599,
        "y": 425,
        "index": 213
    },
    {
        "x": 520,
        "y": 478,
        "index": 214
    },
    {
        "x": 504,
        "y": 466,
        "index": 215
    },
    {
        "x": 488,
        "y": 455,
        "index": 216
    },
    {
        "x": 475,
        "y": 444,
        "index": 217
    },
    {
        "x": 452,
        "y": 437,
        "index": 218
    },
    {
        "x": 434,
        "y": 437,
        "index": 219
    },
    {
        "x": 537,
        "y": 464,
        "index": 220
    },
    {
        "x": 538,
        "y": 444,
        "index": 221
    },
    {
        "x": 538,
        "y": 424,
        "index": 222
    },
    {
        "x": 533,
        "y": 407,
        "index": 223
    },
    {
        "x": 517,
        "y": 395,
        "index": 224
    },
    {
        "x": 502,
        "y": 384,
        "index": 225
    },
    {
        "x": 487,
        "y": 373,
        "index": 226
    },
    {
        "x": 471,
        "y": 361,
        "index": 227
    },
    {
        "x": 456,
        "y": 350,
        "index": 228
    },
    {
        "x": 441,
        "y": 338,
        "index": 229
    },
    {
        "x": 427,
        "y": 327,
        "index": 230
    },
    {
        "x": 493,
        "y": 362,
        "index": 231
    },
    {
        "x": 514,
        "y": 360,
        "index": 232
    },
    {
        "x": 532,
        "y": 358,
        "index": 233
    },
    {
        "x": 561,
        "y": 440,
        "index": 234
    },
    {
        "x": 567,
        "y": 422,
        "index": 235
    },
    {
        "x": 566,
        "y": 398,
        "index": 236
    },
    {
        "x": 560,
        "y": 380,
        "index": 237
    },
    {
        "x": 554,
        "y": 361,
        "index": 238
    },
    {
        "x": 549,
        "y": 342,
        "index": 239
    },
    {
        "x": 542,
        "y": 323,
        "index": 240
    },
    {
        "x": 537,
        "y": 306,
        "index": 241
    },
    {
        "x": 532,
        "y": 287,
        "index": 242
    },
    {
        "x": 528,
        "y": 271,
        "index": 243
    },
    {
        "x": 559,
        "y": 290,
        "index": 244
    },
    {
        "x": 545,
        "y": 276,
        "index": 245
    },
    {
        "x": 532,
        "y": 263,
        "index": 246
    },
    {
        "x": 508,
        "y": 245,
        "index": 247
    },
    {
        "x": 494,
        "y": 232,
        "index": 248
    },
    {
        "x": 482,
        "y": 220,
        "index": 249
    },
    {
        "x": 464,
        "y": 208,
        "index": 250
    },
    {
        "x": 450,
        "y": 202,
        "index": 251
    },
    {
        "x": 431,
        "y": 193,
        "index": 252
    },
    {
        "x": 415,
        "y": 185,
        "index": 253
    },
    {
        "x": 540,
        "y": 172,
        "index": 254
    },
    {
        "x": 536,
        "y": 189,
        "index": 255
    },
    {
        "x": 533,
        "y": 206,
        "index": 256
    },
    {
        "x": 528,
        "y": 225,
        "index": 257
    },
    {
        "x": 517,
        "y": 238,
        "index": 258
    },
    {
        "x": 508,
        "y": 221,
        "index": 259
    },
    {
        "x": 500,
        "y": 205,
        "index": 260
    },
    {
        "x": 491,
        "y": 188,
        "index": 261
    },
    {
        "x": 482,
        "y": 173,
        "index": 262
    },
    {
        "x": 474,
        "y": 159,
        "index": 263
    },
    {
        "x": 466,
        "y": 145,
        "index": 264
    },
    {
        "x": 458,
        "y": 131,
        "index": 265
    },
    {
        "x": 446,
        "y": 110,
        "index": 266
    },
    {
        "x": 431,
        "y": 93,
        "index": 267
    }
];

data.forEach(data => {
    ctx.beginPath();
    ctx.arc(data.x * 2, data.y * 2, 10, 0, 2 * Math.PI);
    ctx.stroke();
    ctx.font = "10px Arial"
    ctx.fillText(data.index, (data.x * 2) - 7, (data.y * 2))
})
