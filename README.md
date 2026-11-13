# FitTrack: beginner setup and user guide

FitTrack is a Java fitness challenge tracker that stores members, workouts and goals. Goals can count exercise minutes or completed workout sessions. The webpage shows totals and goal progress, and provides forms to add, view, edit and delete records.

The project is configured for development in Eclipse IDE using Java 25 and Maven. The repository includes shared Eclipse settings and launch configurations. Java handles the business rules, database access and web requests. HTML, CSS and JavaScript provide the webpage.

## Contents

1. [How the project works](#1-how-the-project-works)
2. [Tools and terminal basics](#2-tools-and-terminal-basics)
3. [Open the project in Eclipse](#3-open-the-project-in-eclipse)
4. [Build and try the app without MySQL](#4-build-and-try-the-app-without-mysql)
5. [Connect to the online Filess.io database](#5-connect-to-the-online-filessio-database)
6. [Create the tables and add sample records](#6-create-the-tables-and-add-sample-records)
7. [View the database records](#7-view-the-database-records)
8. [Optional: install a local MySQL server](#8-optional-install-a-local-mysql-server)
9. [Connect the Java app to MySQL](#9-connect-the-java-app-to-mysql)
10. [Understand environment variables and .env](#10-understand-environment-variables-and-env)
11. [Use the FitTrack webpage](#11-use-the-fittrack-webpage)
12. [Run tests and create Javadoc](#12-run-tests-and-create-javadoc)
13. [Upload changes to GitHub](#13-upload-changes-to-github)
14. [Deploy from GitHub to Vercel](#14-deploy-from-github-to-vercel)
15. [Fix common problems](#15-fix-common-problems)
16. [Understand the files and prepare the assessment](#16-understand-the-files-and-prepare-the-assessment)

## 1. How the project works

The current online setup is:

```text
Browser → Vercel → Java application → JDBC → Filess.io MySQL
```

- **Vercel** runs the Java application and serves its webpage.
- **Filess.io** hosts the MySQL database, where records stay saved.
- **JDBC** lets Java send SQL commands to MySQL.
- **GitHub** stores the source code. It does not store the live database records.
- **Eclipse** opens, edits, builds and tests the Java project.
- **Maven** downloads Java dependencies and builds the application.

The hosted webpage needs an admin or member login. Its address is available in the Vercel project dashboard. The source repository address follows the pattern `https://github.com/zaheeb22/fittrack`.

There are three main database tables:

| Table          | What it stores                    | Link to another table                  |
| -------------- | --------------------------------- | -------------------------------------- |
| `members`    | ID, name, email and age           | The owner of workouts and challenges   |
| `workouts`   | Activity, duration and date       | `member_id` refers to `members.id` |
| `challenges` | Goal name, type, target and dates | `member_id` refers to `members.id` |

There are two server modes. `--memory` keeps records temporarily and loses them when the app stops. `--mysql` keeps records in MySQL. The hosted application uses `--mysql`.

## 2. Tools and terminal basics

The commands in this guide use **macOS Terminal with zsh**, the default shell on this Mac. Windows and Linux need the equivalent paths and installation steps.

Tools for development:

- Eclipse IDE with Java and Maven support.
- **JDK 25**: the Java tools used to compile this project.
- Maven, or Eclipse's Maven build support.
- Git for uploading source code.
- Python 3 for the optional API verification script.
- A MySQL client for viewing records. The online database does not require a local MySQL server.

In Terminal, move into the existing project folder:

```sh
cd /Users/YOUR_MAC_USERNAME/eclipse-workspace/fittrack
```

Replace `YOUR_MAC_USERNAME` with your Mac account name, `YOUR_GITHUB_USERNAME` with your GitHub username, and `YOUR_APP` with your Vercel app name. These are placeholders, not literal values. On another computer, use the folder where you cloned the repository. Commands using `./scripts/` or `database/` assume Terminal is inside the project folder.

```sh
pwd
ls
```

`pwd` shows the current folder. `ls` lists its files. The project folder contains `pom.xml`, `src`, `scripts` and `database`.

Check installed command-line tools:

```sh
java -version
mvn -version
git --version
python3 --version
```

Java should report version 25 for this build. On macOS with a separately installed JDK 25, these commands select it for the current Terminal window:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

If Java is only bundled with Eclipse, the included scripts can find Eclipse's Java runtime on this Mac. A normal Maven installation is still recommended for a new computer. With [Homebrew](https://brew.sh/) already installed, `brew install maven` installs Maven. Eclipse can also run Maven without a separate terminal installation. JDK downloads are available from [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25).

### Terminal commands and SQL commands are different

A prompt ending in `%` or `$` is the normal Terminal shell. A prompt such as `mysql>` or `MySQL ... SQL>` is a connected database session.

- `cd`, `git`, `export` and `./scripts/run` belong in **Terminal**.
- `SHOW TABLES;` and `SELECT ...;` belong in a **MySQL SQL session**.
- SQL statements normally end with `;`.
- `\source` is a MySQL Shell command, not a Java command.
- Do not copy the prompt itself when copying commands.

## 3. Open the project in Eclipse

1. Open Eclipse and choose **File → Import → Maven → Existing Maven Projects**.
2. Select the project folder containing `pom.xml` and finish the import.
3. Under **Preferences → Java → Installed JREs**, add or select a Java 25 JDK. On macOS, Preferences may be called Settings.
4. Check **Project → Properties → Java Build Path** uses Java 25.
5. Right-click the project and select **Maven → Update Project**. Maven downloads the libraries listed in `pom.xml`.

The Eclipse files are `.project`, `.classpath` and `.settings/`. `pom.xml` supplies Maven's dependencies and build steps. Keeping these files lets someone else open the project with the same basic IDE setup.

### Build inside Eclipse

Right-click the project → **Run As → Maven build…**. Enter these goals:

```text
clean verify javadoc:javadoc
```

The Eclipse Console should finish with `BUILD SUCCESS`.

### Run inside Eclipse

The `eclipse/` folder contains two shared launch configurations:

| Configuration | Use |
| --- | --- |
| `FitTrack Memory.launch` | Temporary local records without a database |
| `FitTrack MySQL.launch` | Records stored in the configured MySQL database |

1. Refresh the imported project with **File → Refresh** or F5.
2. Expand `eclipse/`, right-click **FitTrack Memory.launch** and choose **Run As → FitTrack Memory**. If this option is not shown, open **Run → Run Configurations → Java Application** and select **FitTrack Memory**.
3. Open `http://127.0.0.1:8081` after the Eclipse Console reports that the server is listening.
4. Stop the running app with the red **Terminate** button before starting another configuration on the same port.

For database mode, first copy **FitTrack MySQL** with the **Duplicate** button in Run Configurations. Give the copy a local name such as **FitTrack MySQL Local**. In its **Common** tab, select **Save as → Local file** so private settings remain in your workspace rather than the shared `.launch` file. In its **Environment** tab, add `FITTRACK_DB_URL`, `FITTRACK_DB_USER` and `FITTRACK_DB_PASSWORD` using section 9. Add `FITTRACK_ADMIN_USER` and `FITTRACK_ADMIN_PASSWORD` to test administrator and member sign-in. Apply the settings and run the local copy.

Both shared configurations use `127.0.0.1:8081` for the local web server. The MySQL server's host and port belong in `FITTRACK_DB_URL`. Eclipse does not automatically read `.env`. Keep passwords in the local configuration and check that they are not visible when taking screenshots.

### Test and demonstrate in Eclipse

1. Right-click `src/test/java` and select **Run As → JUnit Test**.
2. Open the **JUnit** view and inspect the failures and errors, rather than relying only on a test count.
3. Run the Maven goals above inside Eclipse and inspect the Console for `BUILD SUCCESS`.
4. Open the generated `target/reports/apidocs/index.html` to review Javadoc.
5. Capture the Eclipse Project Explorer, relevant source classes, successful Maven Console and passing JUnit view for the assessment evidence.

The configuration files support an Eclipse workflow. Screenshots of your actual build, tests and explanation in Eclipse provide evidence of using that workflow.

## 4. Build and try the app without MySQL

In **Terminal**, inside the project folder:

```sh
./scripts/mvn clean verify javadoc:javadoc
```

This compiles the code, runs the tests, creates the application JAR and creates Javadoc. The first build needs internet access to download dependencies. On a computer with Java 25 and Maven already configured, `mvn clean verify javadoc:javadoc` does the same build.

Start the temporary demonstration:

```sh
FITTRACK_PORT=8081 ./scripts/run --memory
```

Open [http://127.0.0.1:8081](http://127.0.0.1:8081). Keep this Terminal window open while using the app. **Ctrl+C** stops it. Records in this mode disappear when it stops.

The original console demonstration is also available:

```sh
./scripts/run --demo
```

To start the packaged application directly when Java 25 is on PATH:

```sh
java -jar target/fittrack-0.0.1-SNAPSHOT.jar --memory
```

That command uses port 8080 unless an environment variable changes it.

## 5. Connect to the online Filess.io database

Filess.io hosts the MySQL server. A client on your computer connects to it; installing MySQL Shell or Workbench does not create another online database.

The current database uses **MySQL 5.7.38**. In **Filess.io → Shared → your database → Overview**, the connection details are:

| Detail | Current project value |
| --- | --- |
| Host | `684dco.h.filess.io` |
| Port | `3306` |
| Database | `fittrack_playpullno` |
| User | `fittrack_playpullno` |
| Password | Copy privately from the Filess.io panel |

A recreated database can have different details. Keep passwords out of the source files and GitHub. The database password is separate from the FitTrack administrator password.

### View tables in the browser

Choose **Web Client** in the Filess.io panel. Open the database connection, then refresh its table list. A new SQL editor can run the commands in sections 6 and 7. The panel also has a database terminal: press Enter inside it to connect.

### Connect using MySQL Shell

Install [MySQL Shell](https://dev.mysql.com/downloads/shell/) separately if `mysqlsh` is unavailable. In Terminal:

```sh
mysqlsh --sql \
  --host=684dco.h.filess.io \
  --port=3306 \
  --user=fittrack_playpullno \
  --password \
  --ssl-mode=REQUIRED \
  --database=fittrack_playpullno
```

`--password` asks for the password privately. Typed characters may remain invisible. This example requires an encrypted connection. It does not use a certificate downloaded from the previous provider. If the server rejects TLS, check the current provider settings before changing the connection's security requirements.

In the connected SQL session:

```sql
SELECT DATABASE(), VERSION();
SHOW SESSION STATUS LIKE 'Ssl_cipher';
```

The database name should be `fittrack_playpullno`. A non-empty `Ssl_cipher` value indicates that this connection uses TLS. `\quit` closes MySQL Shell.

### Connect using Workbench

Create a connection using the host, port, username and database above. Choose **Require** in the SSL settings and enter the password privately. A CA file is only needed when configuring certificate verification with a certificate supplied by the current provider. Do not assume a bundled MySQL Shell path exists inside Workbench; install Shell separately when needed.

### MySQL 5.7 behaviour

MySQL 5.7 ignores `CHECK` constraints. FitTrack still validates positive values, goal types and date rules in Java. Primary keys, unique keys and foreign keys remain relevant database checks. A direct SQL edit can bypass Java validation, so the database should not be described as enforcing the ignored `CHECK` rules. A local MySQL 8.4 installation behaves differently in this respect.

## 6. Create the tables and add sample records

A database is the container; its tables hold the records. The application expects the tables to exist before it starts in MySQL mode.

### Filess.io: create tables inside fittrack_playpullno

In the connected **MySQL Shell SQL session**:

```sql
USE fittrack_playpullno;
```

Then load the table definitions. This is a **MySQL Shell command**, using the current project's full path:

```text
\source /Users/YOUR_MAC_USERNAME/eclipse-workspace/fittrack/database/schema.sql
```

On another computer, replace that path with the actual project path. `database/schema.sql` creates the three main tables, `login_sessions` and `member_accounts` without creating a database or inserting sample people. Existing tables are left in place; this script does not update an older table structure.

Confirm the tables:

```sql
SHOW TABLES;
```

Expected names: `members`, `workouts`, `challenges`, `login_sessions` and `member_accounts`. The first three hold project records. The other two hold sessions and member login details.

### Add the sample records

In the same **MySQL Shell SQL session**:

```text
\source /Users/YOUR_MAC_USERNAME/eclipse-workspace/fittrack/database/sample-data.sql
```

This script uses the currently selected database and adds four fictional members, eight workouts and four challenges. It keeps existing records. Repeating it skips sample IDs already present. If a sample member's ID or email conflicts, that member is skipped; dependent samples require the expected matching ID and email.

The SQL file contains setup commands. After it runs, the records live in **MySQL**, and the app reads them from there. A GitHub push alone does not execute SQL or change the database. Refresh the webpage to load newly inserted records.

### Workbench alternative

Open `database/schema.sql` with **File → Open SQL Script**, select `fittrack_playpullno` as the default schema, and execute the whole script. Then open and execute `database/sample-data.sql` if sample records are wanted. These are SQL scripts, not CSV files.

## 7. View the database records

These commands belong in a connected **SQL session**:

```sql
SHOW DATABASES;
USE fittrack_playpullno;
SHOW TABLES;
DESCRIBE members;
SELECT * FROM members ORDER BY id LIMIT 100;
SELECT * FROM workouts ORDER BY id LIMIT 100;
SELECT * FROM challenges ORDER BY id LIMIT 100;
```

`DESCRIBE` shows column names and types. `SELECT` displays records. `Empty set` means the query found no matching rows; it does not mean the table is missing. For local setup, use `USE fittrack;` instead of `USE fittrack_playpullno;`.

To see each workout alongside its member name:

```sql
SELECT w.id, m.name, w.activity_type, w.duration_minutes, w.workout_date
FROM workouts AS w
JOIN members AS m ON m.id = w.member_id
ORDER BY w.id;
```

To count the records:

```sql
SELECT COUNT(*) AS member_count FROM members;
SELECT COUNT(*) AS workout_count FROM workouts;
SELECT COUNT(*) AS challenge_count FROM challenges;
```

For a simple database check, add or edit a member in the webpage and run the matching `SELECT` again. Closing a database client does not delete saved records.

## 8. Optional: install a local MySQL server

**Skip this section when using Filess.io only.** It is an alternative for running the whole project on one computer. A local database is separate from Filess.io; changes to one do not appear in the other.

The following first-install route uses **Homebrew on macOS**. It installs MySQL 8.4, which supports this project's constraints. If MySQL is already installed using Oracle's installer, use that installation's instructions rather than starting a second server on the same port.

### Install and start MySQL

With [Homebrew](https://brew.sh/) installed, run in **Terminal**:

```sh
brew install mysql@8.4
brew services start mysql@8.4
export PATH="$(brew --prefix mysql@8.4)/bin:$PATH"
mysql --version
mysql_secure_installation
```

The `export` makes this MySQL installation's commands available in the current Terminal. Repeat it in new windows, or add that export line to your own `~/.zshrc`. `mysql_secure_installation` guides the initial account setup: choose a root password, remove anonymous users and the test database, and keep remote root login disabled. A fresh Homebrew installation starts without a root password; follow the prompts for your installed version. [Homebrew&#39;s MySQL 8.4 page](https://formulae.brew.sh/formula/mysql@8.4) documents installation and service commands.

Connect as the database administrator from **Terminal**:

```sh
mysql -u root -p
```

The `-p` prompts for the root password. This opens the classic `mysql>` SQL client.

### Create the local database and tables

At the **mysql> SQL prompt**:

```text
SOURCE /Users/YOUR_MAC_USERNAME/eclipse-workspace/fittrack/database/fittrack.sql;
```

This creates the local `fittrack` database, the three main tables, session and member-account tables, and a small set of demonstration records. The classic `mysql` client uses `SOURCE file;`; MySQL Shell can use `\source file`.

Check the result:

```sql
USE fittrack;
SHOW TABLES;
```

The sample script uses the database selected by the client. The local `fittrack.sql` already includes its own sample records; there is no need to import the larger sample set unless it is wanted.

### Create a separate application account

At the **SQL prompt**, replace the placeholder password with a new local application password:

```sql
CREATE USER 'fittrack_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_LOCAL_APP_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON fittrack.* TO 'fittrack_app'@'localhost';
```

This account can read and change FitTrack records without receiving full administrator access. These commands are for the first creation of the account. If it already exists, use its existing credentials or change its password deliberately with `ALTER USER`.

Exit the SQL client:

```sql
EXIT;
```

The application uses `fittrack_app`, not the root account. Root remains the administrator used for setup. MySQL may retain SQL command history locally, so do not share history files or screenshots containing passwords.

For later service management, these are **Terminal** commands:

```sh
brew services list
brew services stop mysql@8.4
brew services start mysql@8.4
```

Stopping MySQL stops database access until it is started again; it does not remove the data.

## 9. Connect the Java app to MySQL

Stop an existing Java instance with Ctrl+C before starting another on the same port. Build the project first if the JAR does not exist.

Choose **one** of the following configurations in **Terminal**, not at a SQL prompt.

### Use Filess.io from the local Java app

```sh
export FITTRACK_DB_URL='jdbc:mysql://684dco.h.filess.io:3306/fittrack_playpullno?sslMode=REQUIRED'
export FITTRACK_DB_USER='fittrack_playpullno'
read -s 'FITTRACK_DB_PASSWORD?Filess.io database password: '
printf '\n'
export FITTRACK_DB_PASSWORD
export FITTRACK_BIND_HOST='127.0.0.1'
export FITTRACK_PORT='8081'
./scripts/run --mysql
```

`read -s` is zsh syntax and hides password entry. `sslMode=REQUIRED` requires an encrypted JDBC connection. It does not verify the server identity. Stronger verification needs the current provider’s certificate and suitable Java trust-store settings.

### Use the optional local database

```sh
export FITTRACK_DB_URL='jdbc:mysql://localhost:3306/fittrack'
export FITTRACK_DB_USER='fittrack_app'
read -s 'FITTRACK_DB_PASSWORD?Local application database password: '
printf '\n'
export FITTRACK_DB_PASSWORD
export FITTRACK_BIND_HOST='127.0.0.1'
export FITTRACK_PORT='8081'
./scripts/run --mysql
```

Use the password created for `fittrack_app`, not the root password. The startup message should say **FitTrack MySQL server listening**. Open [http://127.0.0.1:8081](http://127.0.0.1:8081).

These settings apply to processes launched from that Terminal window. They do not automatically apply to Eclipse, another Terminal window, or Vercel. For Eclipse, enter the same names and values in **Run Configurations → Environment** and set the argument to `--mysql`.

### The two passwords have different jobs

| Variable                    | Job                                          |
| --------------------------- | -------------------------------------------- |
| `FITTRACK_DB_PASSWORD`    | Lets Java connect to MySQL                   |
| `FITTRACK_ADMIN_PASSWORD` | Lets a person sign in to the webpage and API |

Local loopback access can run without admin variables. If admin variables are already set, the sign-in checks still apply. Both admin variables must be present together; the password needs at least 16 characters and the username cannot contain `:`. `FITTRACK_BIND_HOST=0.0.0.0` allows access from outside the container and requires these sign-in details.

## 10. Understand environment variables and .env

Environment variables are settings supplied when the application starts. They keep database details out of Java source code. The tracked `.env.example` lists the names with no real passwords.

**The Java app does not automatically read `.env`.** A local file is optional. Terminal exports, Eclipse's Environment tab and Vercel's Environment Variables are supported ways to supply settings.

For an optional local file, run in **Terminal**:

```sh
cp .env.example .env
chmod 600 .env
```

This is a first-time copy. If `.env` already contains your settings, edit it instead of overwriting it. `.env` is ignored by Git. `.env.example` remains tracked as the reusable template.

Edit `.env` locally. For a loopback-only MySQL run without webpage sign-in, its contents can follow this pattern:

```dotenv
FITTRACK_DB_URL='jdbc:mysql://YOUR_HOST:YOUR_PORT/YOUR_DATABASE?sslMode=REQUIRED'
FITTRACK_DB_USER='YOUR_DATABASE_USER'
FITTRACK_DB_PASSWORD='YOUR_PRIVATE_DATABASE_PASSWORD'
FITTRACK_BIND_HOST='127.0.0.1'
FITTRACK_PORT='8081'
```

Replace the placeholders; keep quotes around values. For local MySQL, use the localhost URL from section 9. To enable webpage sign-in, also add `FITTRACK_ADMIN_USER` and a `FITTRACK_ADMIN_PASSWORD` of at least 16 characters. Remove the template's admin entries if you do not want local sign-in. Passwords containing a single quote need proper shell escaping; interactive password entry avoids that issue.

Load your own trusted shell-format `.env` from **Terminal** before starting Java:

```sh
set -a
source .env
set +a
./scripts/run --mysql
```

`set -a` exports the file's variables to Java. `source` runs the file as shell code; this is why these instructions apply to your own file with shell-compatible values. Loading a file does not clear variables already set in the Terminal. To clear an old local sign-in configuration explicitly:

```sh
unset FITTRACK_ADMIN_USER FITTRACK_ADMIN_PASSWORD
```

That does not remove Vercel's sign-in settings. Never commit a real `.env`, database password, admin password, token or private key.

## 11. Use the FitTrack webpage

Start with **Members**, add a member, then record a workout for that person. Create a challenge with a target and a start and end date.

- **Overview** shows member count, workout count, exercise minutes and completed goals.
- **Members** provides member details and edit/delete actions.
- **Workouts** records completed activities and their duration.
- **Challenges** supports `MINUTES` and `SESSIONS` goals.
- **Refresh** reloads records, including changes made directly in MySQL.

Only a member's workouts inside the challenge dates count towards their goal. Both boundary dates count. A member cannot be deleted while workouts or challenges still refer to them; delete those records first. IDs are unique per table. The form suggests an ID; an existing record's ID stays fixed when editing.

### Admin and member access

The app has two kinds of account. The admin login uses the environment variables. Each member login is linked to one member profile in the database. A member cannot choose another member's profile or make themselves an admin.

| Action | Admin | Member |
| --- | --- | --- |
| Read profiles, workouts and challenges | Everyone's records | Own records only |
| Add or edit profiles, workouts and challenges | Yes | No |
| Delete profiles and workouts | Yes, subject to linked-record checks | No |
| Delete challenges and fitness goals | Any member's goals | Own goals only |
| Create, reset or remove member logins | Yes | No |

Here, a workout plan means an existing challenge or fitness goal. Completed workouts remain separate records. These rules are checked by Java for every API request, including requests made outside the webpage.

### Give a member a login

1. Start the app in MySQL mode with admin sign-in enabled, then sign in as the admin.
2. Open **Members**. Add the member's profile first if it does not exist.
3. Choose **Set up login** beside that member. Enter a unique username and a password of 16 to 200 characters. Usernames allow letters, numbers and `@ . _ + -`, with 3 to 100 characters. The member email is suggested; it can be replaced. Usernames are not case-sensitive.
4. Save the login. Share the details with that member privately.
5. Log out. The member signs in at the same `/login` page with their own details. They see **My profile**, their workout history and their challenges. Their challenges have a Delete button. They cannot add or edit records.

**Reset login** replaces the username and password and ends earlier sessions for that account. **Remove login** ends access while keeping the profile, workouts and challenges. Deleting a member's profile also removes their login, after the admin has removed the linked workouts and challenges. Members ask the admin to correct their details or reset a forgotten password; there is no self-registration or email reset feature.

Member passwords are saved as salted PBKDF2-HMAC-SHA256 hashes with 600,000 iterations, following the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). A salt is a different random value for each saved password. The API never returns passwords or password hashes. Member passwords do not go in `.env`; that file holds the single admin login and database connection settings.

Memory mode can demonstrate these accounts too, but the accounts and records disappear when the app stops. MySQL mode saves them between restarts. Local mode without sign-in gives full access for demonstrations and cannot manage member logins.

### Sign in and log out

When sign-in is enabled, the app opens `/login`. The admin enters the values set in `FITTRACK_ADMIN_USER` and `FITTRACK_ADMIN_PASSWORD`. Members enter the details given to them by the admin. The **Log out** button is beside Refresh in the dashboard header.

Signing in creates a random session cookie. Logging out deletes the saved session and clears that cookie, then returns to the login page. The old cookie cannot be reused. The browser password popup is no longer used, and Basic authentication headers no longer grant API access.

Sessions last up to eight hours. In MySQL mode, session hashes are saved in `login_sessions` so logout works across hosted app instances. The table contains only a token hash and expiry time, not member records or passwords. A hash is a one-way value used to check the session code without saving the original code. Changing the admin credentials makes earlier sessions unusable. If sign-in is disabled for a local demonstration, no login is required and the logout button stays hidden.

The app checks for `login_sessions` and `member_accounts` at startup when MySQL and admin sign-in are enabled. If either table is missing and the database account can create tables, the app creates it automatically. Otherwise, a database administrator can run the updated `database/schema.sql` once; existing member, workout and challenge records remain unchanged. The application database account needs SELECT, INSERT, UPDATE and DELETE access to these tables, already covered by the local `fittrack.*` grant above.

Cookies are HttpOnly and SameSite=Strict, and hosted cookies are marked Secure. Sign-in and record-changing requests need `X-FitTrack-Request: true`; cross-site requests are rejected. Five wrong sign-ins from one connection address temporarily block further attempts on that app instance for five minutes. This is a basic limit, not a shared account lockout system. The cookie and request checks use the approaches described in the [OWASP session guide](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) and [OWASP cross-site request guide](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html).

### API addresses

The Java server also provides a REST API:

| Address                                                     | Supported actions                                                                     |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `/members`, `/workouts`, `/challenges`                | GET lists records; POST adds a record                                                 |
| `/members/{id}`, `/workouts/{id}`, `/challenges/{id}` | GET reads; PUT updates; DELETE removes                                                |
| `/members/{id}/summary`                                   | GET returns workout count, total minutes and longest duration                         |
| `/challenges/{id}/progress`                               | GET returns achieved amount, target, percentage and completion                        |
| `/auth/login` | POST checks admin or member credentials and sets the session cookie |
| `/accounts` | Admin-only GET lists member usernames without hashes |
| `/accounts/{memberId}` | Admin-only PUT creates or resets a login; DELETE removes it |
| `/auth/logout` | POST ends the session and clears the cookie |
| `/auth/session` | GET reports sign-in status, account role and member ID |
| `/api`                                                    | GET returns application information                                                   |
| `/health`                                                 | GET returns a small status response without sign-in; it is not a database health test |

Requests that send record data use JSON and `Content-Type: application/json`. With admin sign-in enabled, first POST the username and password as JSON to `/auth/login`, retain the returned cookie, and send `X-FitTrack-Request: true` with each POST, PUT and DELETE. The API demo script does this automatically. These record actions are subject to the account permissions above. Common responses are 200 for success, 201 for creation, 204 for deletion, 400 for invalid input, 401 for missing/wrong sign-in, 403 for an action the account cannot perform, 404 for a missing record, and 409 for a duplicate or linked-record conflict.

Example member JSON:

```json
{"id":7001,"name":"Demo Person","email":"demo7001@example.com","age":25}
```

Example workout JSON for that member:

```json
{"id":7001,"memberId":7001,"activityType":"Walking","durationMinutes":30,"date":"2026-10-01"}
```

Example challenge JSON:

```json
{"id":7001,"memberId":7001,"name":"October goal","type":"MINUTES","target":60,"startDate":"2026-10-01","endDate":"2026-10-31"}
```

Workouts cannot be future-dated. Use an appropriate completed date when repeating examples. The form is the easiest way to create records; the next section provides an API verification script.

## 12. Run tests and create Javadoc

In **Terminal**:

```sh
./scripts/mvn clean verify javadoc:javadoc
```

The JUnit tests cover calculations, challenge types, service rules, JDBC storage, HTTP handling, login, logout, session expiry, shared database sessions, member permissions and account resets. Test results are generated in `target/surefire-reports/`.

Database tests use **H2 in MySQL compatibility mode**. They check JDBC operations but do not replace checking the actual MySQL server. The Filess.io database contains imported sample records. A full recorded check of hosted MySQL CRUD, login and persistence after restart is still needed.

In Eclipse, select `src/test/java` → **Run As → JUnit Test**. Passing tests appear in the JUnit view. Generate the final report's test evidence from your own current run.

Open the generated Javadoc on macOS:

```sh
open target/reports/apidocs/index.html
```

Javadoc explains public Java classes and methods. It is generated from the `/** ... */` comments.

### Verify HTTP CRUD against a running server

Keep the Java server running. Open a **second Terminal window**, move into the project folder, then run:

```sh
FITTRACK_API_URL=http://127.0.0.1:8081 python3 scripts/api_demo.py
```

The script uses reserved ID `900001`, checks it is unused, adds and edits test records, checks progress and deletion rules, and removes its own records afterwards. It performs real changes on the server you select. Use a test database or ensure you intend to run these sample operations against that server.

For the hosted app, set the admin credentials privately in that second Terminal before running the script:

```sh
read 'FITTRACK_ADMIN_USER?FitTrack sign-in username: '
read -s 'FITTRACK_ADMIN_PASSWORD?FitTrack sign-in password: '
printf '\n'
export FITTRACK_ADMIN_USER FITTRACK_ADMIN_PASSWORD
FITTRACK_API_URL=https://YOUR_APP.vercel.app python3 scripts/api_demo.py
```

The script uses HTTPS when sending credentials to an online server. Do not put credentials in URLs or paste them into chat.

### Check the login and logout buttons

The optional `scripts/test-login.cjs` checks the login form and logout action using test-only credentials on a local server. It does not use the live database. Start a separate server in one Terminal:

```sh
FITTRACK_PORT=8094 FITTRACK_ADMIN_USER=tester FITTRACK_ADMIN_PASSWORD='local-test-password-only' ./scripts/run --memory
```

In another Terminal, after installing jsdom as described below:

```sh
NODE_PATH=/private/tmp/fittrack-ui-check/node_modules FITTRACK_API_URL=http://127.0.0.1:8094 FITTRACK_ADMIN_USER=tester FITTRACK_ADMIN_PASSWORD='local-test-password-only' node scripts/test-login.cjs
```

The test checks that signing in clears the password form, the logout button appears, logout clears the cookie, and the old session is rejected. These are local demonstration credentials, not the credentials for the hosted app.

### Verify records survive a restart

In MySQL mode, add a separate test member through the webpage. Find it with `SELECT` in MySQL, stop and restart the local Java application with the same database settings, then check it is still present. Edit and delete it through the webpage and confirm each change with SQL. For the hosted app, redeploying the application should also preserve records in the external Filess.io database.

### Optional webpage action checks

`scripts/test-dashboard.cjs` uses Node.js and jsdom to check forms, navigation and messages. It is extra development tooling, not a dependency of the app. It checks webpage behavior rather than visual appearance.

These checks require a **separate empty memory-mode server**; they remove records they create and must not be pointed at the shared Filess.io database. With Node.js and npm installed, start that server in one Terminal:

```sh
FITTRACK_PORT=8092 ./scripts/run --memory
```

In another Terminal, install the test-only tool outside the repository and run the checks:

```sh
npm install --prefix /private/tmp/fittrack-ui-check jsdom@26.1.0
NODE_PATH=/private/tmp/fittrack-ui-check/node_modules FITTRACK_API_URL=http://127.0.0.1:8092 node scripts/test-dashboard.cjs
```

Rebuild the JAR after webpage changes before running this check.

## 13. Upload changes to GitHub

Git stores a history of source changes. A **commit** records a set of changes locally. A **push** uploads commits to GitHub. A clone of this repository already has a Git repository and `origin` configured.

### First setup on a new computer

Install Git if needed, then set the name and email to use on your commits:

```sh
git config --global user.name "YOUR_NAME"
git config --global user.email "YOUR_GITHUB_EMAIL_OR_NOREPLY_EMAIL"
```

A GitHub no-reply email is available in your GitHub email settings. These values identify commits; they do not sign you in. [GitHub&#39;s setup guide](https://docs.github.com/en/get-started/git-basics/set-up-git) explains configuration and authentication.

To download this existing repository:

```sh
git clone https://github.com/zaheeb22/fittrack.git
cd fittrack
```

Import that folder into Eclipse using section 3.

### Push later changes from the existing project

In **Terminal**, inside the project folder:

```sh
git status
git diff
git add README.md
git diff --cached
git commit -m "Update beginner setup guide"
git push origin main
```

This example stages only the README. Replace its filename with the files you intend to upload. `git diff --cached` shows the staged changes before committing. To stage all intended additions, edits and deletions, `git add -A` is available; review `git status` and the staged diff first.

This setup uses `Dockerfile.vercel`. A separate regular `Dockerfile` is not needed.

GitHub HTTPS pushes need supported authentication, such as a credential manager or token; your GitHub account password is not used as the Git password. Tokens belong in the authentication prompt or credential manager, not in source code or remote URLs. GitHub Desktop is an alternative if terminal authentication is difficult.

### Creating a different new repository

Only for a folder that is not already a Git repository: create an empty repository on GitHub, then run the following in that folder. Replace the remote URL with your own repository URL.

```sh
git init -b main
git add -A
git diff --cached
git commit -m "Add FitTrack project"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

This project already has `origin`. Check it with `git remote -v`; do not repeat `git remote add origin` on an existing setup. If GitHub already contains changes you do not have, inspect them and use `git pull --rebase origin main` with a clean working tree, resolve any conflicts, then push. Do not force-push to bypass a rejected push.

### Files excluded from GitHub

`.gitignore` keeps `.env`, private settings, private keys, build output, dependency caches, Python caches and `docs/` out of new commits. `.env.example` is tracked because it is a blank template. An ignore rule does not remove a file already tracked; `git ls-files docs` can confirm whether anything under `docs/` is tracked.

`docs/` is kept locally for report work and evidence and is intentionally excluded from this repository. The README is self-contained and does not depend on that folder. Assessment documentation still needs to be included in the final submission. Check repository visibility in GitHub settings; a public repository can be read by anyone.

## 14. Deploy from GitHub to Vercel

`Dockerfile.vercel` builds the Java project, runs its tests and starts the packaged application in MySQL mode. It is the deployment file for this project. The regular `Dockerfile` is not required for this Vercel setup.

[Vercel&#39;s container runtime documentation](https://vercel.com/docs/functions/runtimes/container) describes Dockerfile detection, HTTP ports and deployment limits. The container runtime is currently beta. Check current provider plans and usage limits before relying on a free tier.

### First deployment

1. Push the application source to GitHub.
2. Create the Filess.io database tables using section 6.
3. In Vercel choose **Add New → Project**, connect GitHub and import the `fittrack` repository.
4. Use root directory `./` and the detected **Container** preset. Leave Build, Output and Install overrides unchanged.
5. Add the following environment variables privately for the deployment environments you intend to use.

| Name                        | Value                                                                                           |
| --------------------------- | ----------------------------------------------------------------------------------------------- |
| `PORT`                    | `8081`                                                                                        |
| `FITTRACK_BIND_HOST`      | `0.0.0.0`                                                                                     |
| `FITTRACK_DB_URL`         | `jdbc:mysql://684dco.h.filess.io:3306/fittrack_playpullno?sslMode=REQUIRED` |
| `FITTRACK_DB_USER`        | `fittrack_playpullno`, or a configured application database user                                         |
| `FITTRACK_DB_PASSWORD`    | The matching Filess.io user's database password                                                     |
| `FITTRACK_ADMIN_USER`     | Chosen FitTrack sign-in username, with no colon                                                 |
| `FITTRACK_ADMIN_PASSWORD` | Unique FitTrack sign-in password of at least 16 characters                                      |

Set `FITTRACK_PORT` to `8081` as well, or remove it so Java uses `PORT`. These variables control the web server, not MySQL. Keep `3306` in the database URL only. `FITTRACK_API_URL` is used by test scripts and is not required by the hosted app.

6. Click **Deploy** and inspect the build and runtime logs if it fails.
7. Open the deployment URL and use the login page to sign in with the **FitTrack admin** details, not the Filess.io database credentials. The Log out button ends that session.
8. Add or edit a test record and confirm it appears in MySQL.

Using the same database for Preview and Production means both deployments can change the same records. Use a separate test database if independent preview data is needed.

### Update an existing deployment

A push to the connected production branch normally starts a new Vercel deployment. Check its status in **Deployments**. Editing Vercel environment variables requires a new deployment before the changed values take effect. Use the deployment's menu → **Redeploy** when only settings have changed.

Vercel redeploys do not create tables or execute the sample SQL. Filess.io stores records separately from the application container, so redeploying the Java application does not reset the database.

## 15. Fix common problems

| Problem                                              | Meaning and next step                                                                                                                           |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `mvn: command not found`                           | Install Maven, build through Eclipse, or use`./scripts/mvn` when its supported Maven location exists.                                         |
| Java version or compiler error                       | Check the build uses JDK 25 in both Eclipse and Terminal.                                                                                       |
| `Build first: ./scripts/mvn package`               | The JAR is missing. Build the project before using`scripts/run`.                                                                              |
| `Address already in use`                           | Another server has the port. Stop the old instance or choose another`FITTRACK_PORT`.                                                          |
| `Set FITTRACK_DB_URL` or another missing setting   | Supply that environment variable in the process that launches Java.`.env` is not loaded automatically.                                        |
| `Use a sign-in password of at least 16 characters` | Set a longer`FITTRACK_ADMIN_PASSWORD`; redeploy if hosted.                                                                                    |
| HTTP 401                                             | Sign-in is required or the credentials are incorrect. Use the admin variables or the member login given by the admin. Sign in again at `/login` if the session has ended. |
| `Access denied for user`                           | Check the database username, password and account permissions. This is separate from webpage sign-in.                                           |
| `No database selected`                             | Run`USE fittrack_playpullno;` for Filess.io or `USE fittrack;` locally before table commands.                                                             |
| `Table ... doesn't exist`                          | Create the tables in the same database named in`FITTRACK_DB_URL`.                                                                             |
| Connection timeout or unknown host                   | Check the host, port, network and whether the Filess.io service is running.                                                                         |
| CA file or certificate error                         | Check the certificate path and use certificate details supplied by the current provider; a certificate from a different service will not verify this server.                                                      |
| Workbench keeps spinning                             | Try MySQL Shell and inspect Workbench logs for the actual error.                                                                                |
| Records disappear after stopping Java                | Check that Java was started with`--mysql`; memory mode does not save records.                                                                 |
| Duplicate ID or email                                | Use an unused ID/email, or edit the existing record.                                                                                            |
| Member deletion is blocked                           | Delete that member's workouts and challenges first.                                                                                             |
| Vercel forwards to a different port | Set `PORT` and `FITTRACK_PORT` to `8081`, and `FITTRACK_BIND_HOST` to `0.0.0.0`, then redeploy. MySQL port `3306` belongs in the JDBC URL. |
| Vercel HTTP 500 / function exited                    | Open the newest runtime log entry and expand all messages to find the actual Java exception.                                                    |
| `origin already exists`                            | The Git remote is already set; inspect it with`git remote -v`.                                                                                |
| Git has no user name or email                        | Configure your commit identity using section 13.                                                                                                |
| README changed but GitHub did not                    | Save the file, stage it, commit it, and push the commit.                                                                                        |
| App changes are missing                              | Rebuild the JAR locally, or check that Vercel deployed the intended Git commit.                                                                 |

## 16. Understand the files and prepare the assessment

| File or folder                                      | Purpose                                                                      |
| --------------------------------------------------- | ---------------------------------------------------------------------------- |
| `src/main/java/com/fittrack/model/`               | Member, workout and challenge classes with input checks                      |
| `src/main/java/com/fittrack/service/`             | Rules, relationships and calculations                                        |
| `src/main/java/com/fittrack/repository/`          | Shared storage interface, memory storage and prepared-statement JDBC storage |
| `src/main/java/com/fittrack/web/`                 | HTTP server, webpage file handling and server settings                       |
| `src/main/java/com/fittrack/app/FitTrackApp.java` | Main entry point and mode selection                                          |
| `src/main/resources/web/`                         | HTML, CSS, JavaScript and favicon included in the JAR                        |
| `src/test/java/`                                  | JUnit tests                                                                  |
| `pom.xml`                                         | Java dependencies, build plugins and Java version                            |
| `.project`, `.classpath`, `.settings/`        | Eclipse Java and Maven configuration                                         |
| `database/schema.sql`                             | Creates tables inside an already selected database                           |
| `database/fittrack.sql`                           | Creates the local database, tables and local demonstration records           |
| `database/sample-data.sql`                        | Adds the larger sample set to the selected database                           |
| `scripts/mvn`                                     | Runs Maven, with a temporary development-install fallback                    |
| `scripts/java-env.sh`                             | Finds Java for the workspace helper scripts                                  |
| `scripts/run`                                     | Runs the built JAR                                                           |
| `scripts/api_demo.py`                             | Checks API CRUD and goal progress                                            |
| `scripts/test-login.cjs` | Optional login form and logout checks |
| `scripts/test-dashboard.cjs`                      | Optional webpage action checks                                               |
| `Dockerfile.vercel`                               | Builds and runs the hosted Java application                                  |
| `.dockerignore`                                   | Keeps unnecessary or private files out of the container build input          |
| `.gitignore`                                      | Keeps local files out of new Git commits                                     |
| `.env.example`                                    | Lists example settings with blank password fields                            |
| `target/`                                         | Generated JAR, compiled classes, test results and Javadoc; ignored by Git    |
| `.build/`                                         | Local Maven cache used by the temporary development setup; ignored by Git    |
| `docs/`                                           | Local report, diagrams and evidence; ignored by Git                          |

The design uses an abstract `Challenge` with `MinutesChallenge` and `SessionsChallenge` subclasses, shared repository interfaces, collections and prepared SQL statements. It calculates workout counts, total minutes, longest workouts and goal completion. A running webpage alone does not demonstrate every marking criterion; the tests, code explanation and evidence matter too.

For the final assessment, prepare the Java source and tests, `pom.xml`, SQL setup/population scripts, generated Javadoc, class/schema diagram images or PDFs, report, genuine CRUD and persistence screenshots, and demonstration video. Capture Eclipse's Project Explorer, successful build Console and passing JUnit view as evidence of using Eclipse. Follow Canvas requirements for the final ZIP, deadline and any acknowledgement of sources or assistance.

Keep `.env`, passwords, tokens, private keys and unrelated personal data out of the submission. Keep a backup of the submitted work. Local report files can be included in the assessment ZIP without being published to GitHub.

### Check member access on a local test server

With the separate memory-mode server above running and jsdom installed, this optional check creates two temporary members, workouts and goals. It tests the admin login form, the member's own profile, hidden admin controls and deletion of the member's own goal. It removes its test records afterwards. Use an empty memory-mode server; the script stops if its reserved member IDs `910001` or `910002` already exist.

```sh
NODE_PATH=/private/tmp/fittrack-ui-check/node_modules FITTRACK_API_URL=http://127.0.0.1:8094 FITTRACK_ADMIN_USER=tester FITTRACK_ADMIN_PASSWORD='local-test-password-only' node scripts/test-member-access.cjs
```

These credentials are examples for the separate local test server. They are not the hosted login.
