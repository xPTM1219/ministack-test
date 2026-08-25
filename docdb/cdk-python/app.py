import os

from aws_cdk import App, Environment

from docdb_stack import DocdbStack

app = App(outdir=os.environ.get("CDK_OUTDIR", "cdk.out"))

DocdbStack(
    app,
    "DocdbStack",
    env=Environment(account="000000000000", region="us-east-1"),
)

app.synth()
