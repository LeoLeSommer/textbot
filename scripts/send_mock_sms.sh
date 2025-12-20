#!/bin/bash

# Usage: ./send_mock_sms.sh <phone_number> <message_body>

NUMBER=$1
MESSAGE=$2

if [ -z "$NUMBER" ] || [ -z "$MESSAGE" ]; then
    echo "Usage: $0 <phone_number> <message_body>"
    exit 1
fi

# Send the SMS to the emulator
# Note: This assumes only one emulator is running. 
# If multiple are running, you might need to specify the device ID.
adb shell am broadcast -a android.provider.Telephony.SMS_RECEIVED \
    --es pdus $(printf "0001000B91%s000000" "$NUMBER") # This is a simplified approach, let's use the standard adb emu command

# Use adb emu for real SMS simulation (requires telnet access or auth)
# For simplicity and reliability in most setups, we'll try 'adb shell am broadcast' first
# but actually 'adb emu sms send' is better if supported.
# Let's use a simpler way: adb shell am start -a android.intent.action.SENDTO ... but that's for SENDING.

# The most reliable way for "mocking" a RECEIVED SMS on an emulator:
adb emu sms send "$NUMBER" "$MESSAGE"
