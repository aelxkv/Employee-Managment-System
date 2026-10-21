CREATE DATABASE IF NOT EXISTS ems_db;
USE ems_db;

CREATE TABLE department (
  dept_id INT AUTO_INCREMENT PRIMARY KEY,
  dept_name VARCHAR(100) NOT NULL
);

CREATE TABLE employee (
  emp_id INT AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  email VARCHAR(100),
  phone VARCHAR(20),
  dept_id INT,
  designation VARCHAR(100),
  date_of_joining DATE,
  status VARCHAR(10) DEFAULT 'Active',
  FOREIGN KEY (dept_id) REFERENCES department(dept_id)
);

CREATE TABLE users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash CHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  emp_id INT NULL,
  FOREIGN KEY (emp_id) REFERENCES employee(emp_id)
);

CREATE TABLE attendance (
  attendance_id INT AUTO_INCREMENT PRIMARY KEY,
  emp_id INT NOT NULL,
  date DATE NOT NULL,
  check_in TIME,
  check_out TIME,
  status VARCHAR(20),
  FOREIGN KEY (emp_id) REFERENCES employee(emp_id)
);

CREATE TABLE leave_request (
  leave_id INT AUTO_INCREMENT PRIMARY KEY,
  emp_id INT NOT NULL,
  from_date DATE,
  `to_date` DATE,
  reason VARCHAR(255),
  status VARCHAR(20) DEFAULT 'Pending',
  FOREIGN KEY (emp_id) REFERENCES employee(emp_id)
);

CREATE TABLE payroll (
  payroll_id INT AUTO_INCREMENT PRIMARY KEY,
  emp_id INT NOT NULL,
  month VARCHAR(20),
  basic_salary DECIMAL(12,2),
  allowances DECIMAL(12,2),
  deductions DECIMAL(12,2),
  net_salary DECIMAL(12,2),
  generated_date DATE,
  FOREIGN KEY (emp_id) REFERENCES employee(emp_id)
);

CREATE TABLE task (
  task_id INT AUTO_INCREMENT PRIMARY KEY,
  emp_id INT NOT NULL,
  title VARCHAR(150),
  description TEXT,
  deadline DATE,
  status VARCHAR(20) DEFAULT 'Pending',
  FOREIGN KEY (emp_id) REFERENCES employee(emp_id)
);

INSERT INTO department (dept_name) VALUES ('HR'), ('Engineering'), ('Sales');

-- login: admin / admin123
INSERT INTO users (username, password_hash, role, emp_id)
VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Admin', NULL);
