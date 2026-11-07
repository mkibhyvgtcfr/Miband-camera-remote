package com.miband.camremote.camera

/**
 * ISO 15740 (PTP) wire constants plus the vendor opcodes/properties used by the
 * camera brands supported by this app. Values are kept as Int so Kotlin's
 * unsigned-aware bit operations remain easy to use.
 */
object Ptp {
    const val CONTAINER_COMMAND = 1
    const val CONTAINER_DATA = 2
    const val CONTAINER_RESPONSE = 3
    const val CONTAINER_EVENT = 4

    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_PARAMETER_NOT_SUPPORTED = 0x2006
    const val RC_INCOMPLETE_TRANSFER = 0x2007
    const val RC_INVALID_STORAGE_ID = 0x2008
    const val RC_INVALID_OBJECT_HANDLE = 0x2009
    const val RC_DEVICE_PROP_NOT_SUPPORTED = 0x200A
    const val RC_ACCESS_DENIED = 0x200F
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_INVALID_PARENT_OBJECT = 0x201A
    const val RC_INVALID_DEVICE_PROP_VALUE = 0x201B
    const val RC_SESSION_ALREADY_OPEN = 0x201E
    const val RC_STORE_NOT_AVAILABLE = 0x2016

    const val OP_GET_DEVICE_INFO = 0x1001
    const val OP_OPEN_SESSION = 0x1002
    const val OP_CLOSE_SESSION = 0x1003
    const val OP_GET_STORAGE_IDS = 0x1004
    const val OP_GET_STORAGE_INFO = 0x1005
    const val OP_GET_NUM_OBJECTS = 0x1006
    const val OP_GET_OBJECT_HANDLES = 0x1007
    const val OP_GET_OBJECT_INFO = 0x1008
    const val OP_GET_OBJECT = 0x1009
    const val OP_GET_THUMB = 0x100A
    const val OP_DELETE_OBJECT = 0x100B
    const val OP_INITIATE_CAPTURE = 0x100E
    const val OP_GET_DEVICE_PROP_DESC = 0x1014
    const val OP_GET_DEVICE_PROP_VALUE = 0x1015
    const val OP_SET_DEVICE_PROP_VALUE = 0x1016
    const val OP_GET_PARTIAL_OBJECT = 0x101B
    const val OP_GET_EVENT = 0x1017

    const val OP_NIKON_CAPTURE = 0x90C0
    const val OP_NIKON_AF_DRIVE = 0x90C1
    const val OP_NIKON_CHANGE_CAMERA_MODE = 0x90C2
    const val OP_NIKON_CAPTURE_REC_IN_MEDIA = 0x9207
    const val OP_NIKON_GET_EVENT = 0x90C7
    const val OP_NIKON_DEVICE_READY = 0x90C8
    const val OP_NIKON_START_LIVEVIEW = 0x9201
    const val OP_NIKON_END_LIVEVIEW = 0x9202
    const val OP_NIKON_GET_LIVEVIEW_IMAGE = 0x9203
    const val OP_NIKON_CHANGE_AF_AREA = 0x9205
    const val OP_NIKON_AF_DRIVE_CANCEL = 0x9206
    const val OP_NIKON_START_MOVIE = 0x920A
    const val OP_NIKON_END_MOVIE = 0x920B

    const val OP_CANON_EOS_SET_PROP_EX = 0x9110
    const val OP_CANON_EOS_SET_REMOTE_MODE = 0x9114
    const val OP_CANON_EOS_SET_EVENT_MODE = 0x9115
    const val OP_CANON_EOS_GET_EVENT = 0x9116
    const val OP_CANON_EOS_KEEP_DEVICE_ON = 0x911D
    const val OP_CANON_EOS_BULB_START = 0x9125
    const val OP_CANON_EOS_BULB_END = 0x9126
    const val OP_CANON_EOS_GET_PROP_VALUE = 0x9127
    const val OP_CANON_EOS_REMOTE_RELEASE_ON = 0x9128
    const val OP_CANON_EOS_REMOTE_RELEASE_OFF = 0x9129
    const val OP_CANON_EOS_INITIATE_VIEWFINDER = 0x9151
    const val OP_CANON_EOS_TERMINATE_VIEWFINDER = 0x9152
    const val OP_CANON_EOS_GET_VIEWFINDER_DATA = 0x9153
    const val OP_CANON_EOS_DO_AUTO_FOCUS = 0x9154
    const val OP_CANON_EOS_DRIVE_LENS = 0x9155
    const val OP_CANON_EOS_AF_CANCEL = 0x9160

    // Standard device properties
    const val DPC_BATTERY_LEVEL = 0x5001
    const val DPC_IMAGE_SIZE = 0x5003
    const val DPC_COMPRESSION = 0x5004
    const val DPC_WHITE_BALANCE = 0x5005
    const val DPC_F_NUMBER = 0x5007
    const val DPC_FOCUS_DISTANCE = 0x5009
    const val DPC_FOCUS_MODE = 0x500A
    const val DPC_METERING_MODE = 0x500B
    const val DPC_FLASH_MODE = 0x500C
    const val DPC_EXPOSURE_TIME = 0x500D
    const val DPC_EXPOSURE_PROGRAM = 0x500E
    const val DPC_EXPOSURE_INDEX = 0x500F
    const val DPC_EXPOSURE_BIAS = 0x5010
    const val DPC_DATE_TIME = 0x5011
    const val DPC_STILL_CAPTURE_MODE = 0x5013
    const val DPC_BURST_NUMBER = 0x5018
    const val DPC_BURST_INTERVAL = 0x5019
    const val DPC_TIMELAPSE_NUMBER = 0x501A
    const val DPC_TIMELAPSE_INTERVAL = 0x501B

    // Nikon vendor properties
    const val DPC_NIKON_EXPOSURE_TIME = 0xD100
    const val DPC_NIKON_LIVEVIEW_STATUS = 0xD1A2
    const val DPC_NIKON_LIVEVIEW_PROHIBIT = 0xD1A4
    const val DPC_NIKON_RECORDING_MEDIA = 0xD10B
    const val DPC_NIKON_LIVEVIEW_SELECTOR = 0xD1A6
    const val DPC_NIKON_MOVIE_RECORD = 0xD197
    const val DPC_NIKON_APPLICATION_MODE = 0xD1F0
    const val DPC_NIKON_ISOAUTO = 0xD054
    const val DPC_NIKON_ISO_AUTO = 0xD16A
    const val DPC_NIKON_ISOAUTO_HI_LIMIT = 0xD183
    const val DPC_NIKON_FLASH_MODE = 0xD167
    const val DPC_NIKON_ACTIVE_D_LIGHTING = 0xD14E
    const val DPC_NIKON_LONG_EXPOSURE_NR = 0xD06B
    const val DPC_NIKON_HIGH_ISO_NR = 0xD070
    const val DPC_NIKON_EXPOSURE_DELAY = 0xD06A

    // Canon EOS vendor properties
    const val DPC_EOS_APERTURE = 0xD101
    const val DPC_EOS_SHUTTER_SPEED = 0xD102
    const val DPC_EOS_ISO = 0xD103
    const val DPC_EOS_EXPOSURE_COMP = 0xD104
    const val DPC_EOS_AE_MODE = 0xD105
    const val DPC_EOS_DRIVE_MODE = 0xD106
    const val DPC_EOS_METERING_MODE = 0xD107
    const val DPC_EOS_FOCUS_MODE = 0xD108
    const val DPC_EOS_WHITE_BALANCE = 0xD109
    const val DPC_EOS_COLOR_TEMPERATURE = 0xD10A
    const val DPC_EOS_PICTURE_STYLE = 0xD110
    const val DPC_EOS_BATTERY = 0xD111
    const val DPC_EOS_MODEL_ID = 0xD116
    const val DPC_EOS_AVAILABLE_SHOTS = 0xD11B
    const val DPC_EOS_CAPTURE_DESTINATION = 0xD11C
    const val DPC_EOS_IMAGE_FORMAT = 0xD120
    const val DPC_EOS_SHUTTER_COUNTER = 0xD1AC
    const val DPC_EOS_EVF_MODE = 0xD1B1
    const val DPC_EOS_VF_OUTPUT = 0xD1B0
    const val DPC_EOS_DOF_PREVIEW = 0xD1B2

    const val DATA_TYPE_INT8 = 0x0001
    const val DATA_TYPE_UINT8 = 0x0002
    const val DATA_TYPE_INT16 = 0x0003
    const val DATA_TYPE_UINT16 = 0x0004
    const val DATA_TYPE_INT32 = 0x0005
    const val DATA_TYPE_UINT32 = 0x0006
    const val DATA_TYPE_INT64 = 0x0007
    const val DATA_TYPE_UINT64 = 0x0008
    const val DATA_TYPE_STRING = 0xFFFF

    const val FORM_ENUM = 0x01
    const val FORM_RANGE = 0x02
}
