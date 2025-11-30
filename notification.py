# notifications.py
import os
import boto3


def get_sns_client():
    region = os.environ.get("AWS_REGION", "us-east-2")
    return boto3.client("sns", region_name=region)


def send_notification(subject: str, message: str):
    """
    Send an SNS notification to your topic.
    SNS_TOPIC_ARN must be set in environment variables.
    """
    topic_arn = os.environ.get("SNS_TOPIC_ARN")
    if not topic_arn:
        print("⚠ SNS_TOPIC_ARN not set; skipping notification.")
        return

    sns = get_sns_client()
    sns.publish(
        TopicArn=topic_arn,
        Subject=subject[:100],  # SNS subject max 100 chars
        Message=message,
    )
    print("✅ Notification sent via SNS.")
