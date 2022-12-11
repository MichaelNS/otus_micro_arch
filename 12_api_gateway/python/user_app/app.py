import os
from flask import Flask, request

app = Flask(__name__)

config = {
    'DATABASE_URI': os.environ.get('DATABASE_URI', ''),
}

from sqlalchemy import create_engine
engine = create_engine(config['DATABASE_URI'], echo=True)

@app.route('/me')
def me():
    if not 'X-UserId' in request.headers:
        return "Not authenticated"

    id = request.headers['X-UserId']
    user_info = get_user_by_id(id)

    return user_info

def get_user_by_id(id):
    rows = []
    with engine.connect() as connection:
        result = connection.execute(
            "select id, login, email, first_name, last_name from auth_user "
            "where id={}".format(id))
        rows = [dict(r.items()) for r in result]
    return rows[0]


@app.route("/edit", methods=["POST"])
def edit():
    if not 'X-UserId' in request.headers:
        return "Not authenticated"

    request_data = request.get_json()
    id = request.headers['X-UserId']
    id_requested = request_data['id']
    
    if id != id_requested:
        return "Not authenticated"

    login = request_data['login']
    password = request_data['password']
    email = request_data['email']
    first_name = request_data['first_name']
    last_name = request_data['last_name']
    return edit_user(id, login, password, email, first_name, last_name)

def edit_user(id, login, password, email, first_name, last_name):
    try:
        with engine.connect() as connection:
            connection.execute(
                """
                update auth_user
                set login='{}',
                    password='{}',
                    email='{}',
                    first_name='{}',
                    last_name='{}'
                where id={};
                """.format(login, password, email, first_name, last_name, id))
        return "ok"
    except Exception as ex:
        print(ex)
        return str(ex), 400
        # return "login/email already exists", 400

if __name__ == "__main__":
    app.run(host='0.0.0.0', port='8080', debug=True)

