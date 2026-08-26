# DocumentDB: CDK stacks (Python + Java) + Mongo driver tests

Phase 1 defines the DocumentDB cluster+instance as CDK stacks in both Python and
Java. Phase 2 provides simple runnable smoke tests (pymongo / mongodb-driver-legacy)
that connect to the deployed cluster and exercise insert / find / find_one /
delete_one.

## Layout

```
docdb/
├── cdk-python/            CDK app (Python): requirements.txt, app.py, docdb_stack.py
├── cdk-java/              CDK app (Java 17): pom.xml, src/main/java/com/example/{DocdbApp,DocdbStack}.java
├── python-test/           pymongo smoke test: test_docdb.py
├── java-test/             Mongo driver smoke test (Java 17): pom.xml, .../DocdbTest.java
└── README.md
```

## Stack contents (identical in both languages)

- `Vpc(maxAzs=2)` with the default public/private subnets.
- `docdb.DatabaseCluster` — 1 instance, `masterUser = docdbadmin`, **no explicit
  password** (the CDK docdb L2 generates one into SecretsManager and wires it via
  `{{resolve:secretsmanager:...}}` dynamic references), `engineVersion` /
  instance type from env, `removalPolicy = DESTROY`.
- `CfnOutput`s: cluster id, cluster endpoint, port.
- Environment pinned to the ministack account/region:
  `Environment(account="000000000000", region="us-east-1")`.

### Switchable settings (read at synth time)

| Variable         | Default        | Other tested value |
|------------------|----------------|--------------------|
| `DOCDB_VERSION`  | `5.0.0`        | `8.0.0`            |
| `DOCDB_INSTANCE` | `db.r6g.large` | `db.r5.large`      |

## Prerequisites

```bash
# From the repo root: start ministack and export AWS env vars
./prepare_ministack   # or: docker compose up -d

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_REGION=us-east-1
export MINISTACK_VERSION=1.5.0-full
export AWS_ENDPOINT_URL=http://localhost:4566
```

One-time CDK bootstrap shim — CDK v2 templates resolve a `BootstrapVersion`
parameter from SSM; create it once so `cloudformation deploy` works without a
real bootstrap:

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL ssm put-parameter \
    --name /cdk-bootstrap/hnb659fds/version --type String --value 5
```

Python deps (project venv):

```bash
venv/bin/pip install -r docdb/cdk-python/requirements.txt   # aws-cdk-lib==2.250.0
venv/bin/pip install pymongo                                 # for the test
```

## Synthesize

```bash
# Python
cd docdb/cdk-python
DOCDB_VERSION=5.0.0 DOCDB_INSTANCE=db.r6g.large ../../venv/bin/python app.py
# -> cdk.out/DocdbStack.template.json

# Java
cd docdb/cdk-java
DOCDB_VERSION=5.0.0 DOCDB_INSTANCE=db.r6g.large mvn -q compile exec:java
# -> cdk.out/DocdbStack.template.json
```

Verify the template contains `AWS::DocDB::DBCluster`, `AWS::DocDB::DBInstance`
and `AWS::SecretsManager::Secret`.

## Deploy + connect (ministack)

> **Status:** ministack's CloudFormation engine provisions `AWS::DocDB::*`
> resource types with **real mongo containers** (image `ministack-docdb:cfn-docdb`
> and newer). CDK L2's `{{resolve:secretsmanager:...}}` dynamic references for
> `MasterUsername`/`MasterUserPassword` resolve at deploy time, so
> `cloudformation deploy` of these templates works end-to-end (Path A below).
> The direct-API path (Path B) still works as a fallback.

### Path A — CloudFormation deploy

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL cloudformation deploy \
    --template-file cdk.out/DocdbStack.template.json \
    --stack-name DocdbCdk

aws --endpoint-url=$AWS_ENDPOINT_URL cloudformation describe-stacks \
    --stack-name DocdbCdk --query 'Stacks[0].Outputs'

SECRET_ID=$(aws --endpoint-url=$AWS_ENDPOINT_URL secretsmanager list-secrets \
    --query 'SecretList[?contains(Name, `DocdbClusterSecret`)].[Name]' \
    --output text | head -1)
PASSWORD=$(aws --endpoint-url=$AWS_ENDPOINT_URL secretsmanager get-secret-value \
    --secret-id "$SECRET_ID" --query SecretString --output text | jq -r .password)

# The endpoint address in the outputs is cosmetic; connect to the mongo
# container directly (docker inspect <container> for its IP on the compose
# network). Generated passwords can contain URI-special characters — encode:
#   python3 -c "import urllib.parse,sys; print(urllib.parse.quote_plus(sys.argv[1]))" "$PASSWORD"
export MONGO_URI="mongodb://docdbadmin:${PASSWORD}@<mongo-container-ip>:27017"
```

### Path B — direct docdb API (works today)

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL secretsmanager create-secret \
    --name docdb-master --secret-string '{"username":"docdbadmin","password":"password"}'

aws --endpoint-url=$AWS_ENDPOINT_URL docdb create-db-cluster \
    --db-cluster-identifier docdb-cdk-cluster \
    --engine docdb --engine-version 5.0.0 \
    --master-username docdbadmin --master-user-password password

aws --endpoint-url=$AWS_ENDPOINT_URL docdb create-db-instance \
    --db-instance-identifier docdb-cdk-instance \
    --db-cluster-identifier docdb-cdk-cluster \
    --engine docdb --db-instance-class db.r6g.large

# Real containers listen on localhost starting at DOCDB_BASE_PORT (27117).
export MONGO_URI="mongodb://docdbadmin:password@localhost:27117"
```

## Run the tests

```bash
# Python
cd docdb/python-test && ../../venv/bin/python test_docdb.py
# Expect: insert/find/find_one/delete_one output ending with "All ops succeeded"

# Java
cd docdb/java-test && MONGO_URI="$MONGO_URI" mvn -q compile exec:java
```

Both scripts drop the `test.docdb_test` collection first, insert
`{"_id": 1, "title": "test from cdk", "ts": <iso8601>}`, print all docs, print
the single doc, delete it, and print `All ops succeeded`.

## Switch variants

Re-synth with different variables and confirm them in the template:

```bash
DOCDB_VERSION=8.0.0 DOCDB_INSTANCE=db.r5.large ../../venv/bin/python app.py
grep -E 'EngineVersion|InstanceType' cdk.out/DocdbStack.template.json
```

## Cleanup

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL cloudformation delete-stack --stack-name DocdbCdk
# or for path B:
aws --endpoint-url=$AWS_ENDPOINT_URL docdb delete-db-instance --db-instance-identifier docdb-cdk-instance
aws --endpoint-url=$AWS_ENDPOINT_URL docdb delete-db-cluster --db-cluster-identifier docdb-cdk-cluster
```

## Notes / known gaps

- ministack docdb maps `5.0.0 → mongo:5.0.33` and `8.0.0 → mongo:8.0.29`;
  other versions fall back to mongo 5.
- Endpoints returned by the API are cosmetic (`*.docdb.amazonaws.com`);
  actual connectivity is `localhost:<port>` with ports allocated from
  `DOCDB_BASE_PORT` (default 27117). Use `localhost` in `MONGO_URI`.
- `manageMasterUserPassword`: the aws-cdk-lib 2.250.0 docdb L2 has no such
  prop; omitting the Login password produces the same result (auto-generated
  secret in SecretsManager + dynamic refs on the DBCluster).
