import os
import json

from flask import Flask
from sqlalchemy import create_engine
from flask import request
from metrics import register_metrics
 
app = Flask(__name__)

config = {
    'DATABASE_URI': os.environ.get('DATABASE_URI', ''),
    'HOSTNAME': os.environ['HOSTNAME'],
    'GREETING': os.environ.get('GREETING', 'Hello'),
}

engine = create_engine(config['DATABASE_URI'], echo=True) 
@app.route("/")
def hello():
    return config['GREETING'] + ' from ' + config['HOSTNAME'] + '!'

@app.route("/health")
def health ():
	return '{"status": "ok"}'

@app.route("/version")
def version():
	return'{"version": "0.2"}'


@app.route("/config")
def configuration():
    return json.dumps(config)
 
@app.route('/db', methods=['GET', 'POST', 'PUT', 'DELETE'])
def db():
    id = request.args.get('id')
    name = request.args.get('name')

    if request.method == 'POST':
        rows = []
        with engine.connect() as connection:
            connection.execute("insert into client(id, name) values(" + id + ", '" + name + "');")
        return json.dumps({"id": id, "name": name})


    if request.method == 'GET':
        if id is None:
            rows = []
            with engine.connect() as connection:
                result = connection.execute("select id, name from client;")
                rows = [dict(r.items()) for r in result]
            return json.dumps(rows)
        else:
            rows = []
            with engine.connect() as connection:
                result = connection.execute("select id, name from client where id=" + id + ";")
                rows = [dict(r.items()) for r in result]
            return json.dumps(rows)


    if request.method == 'PUT':
        rows = []
        with engine.connect() as connection:
            connection.execute("update client set name='" + name + "' where id=" + id + ";")
        return ""


    if request.method == 'DELETE':
        rows = []
        with engine.connect() as connection:
            connection.execute("delete from client where id=" + id + ";")
        return ""

    else:
        return 'method not allowed'


@app.route('/metrics')
def metrics():
    from prometheus_client import generate_latest
    return generate_latest()


if __name__ == "__main__":
    register_metrics(app)
    app.run(host='0.0.0.0', port='80', debug=True)

