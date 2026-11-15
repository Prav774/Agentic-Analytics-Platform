[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '..\.env' | Resolve-Path -ErrorAction Stop
$envPath = $envFile.Path
$script:secretValues = [System.Collections.Generic.List[string]]::new()

function Read-EnvFile {
    param([string]$Path)
    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $key = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[-1] -eq '"') -or ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
                $wasDoubleQuoted = $value[0] -eq '"'
                $value = $value.Substring(1, $value.Length - 2)
                if ($wasDoubleQuoted) {
                    $slash = [string][char]92
                    $quote = [string][char]34
                    $value = $value.Replace($slash + $quote, $quote).Replace($slash + $slash, $slash)
                }
            }
            if ($values.ContainsKey($key)) { throw "Duplicate .env assignment for $key." }
            $values[$key] = $value
        }
    }
    return $values
}

function Get-RequiredValue {
    param([hashtable]$Values, [string]$Name)
    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        throw "Required .env value $Name is missing or empty."
    }
    return [string]$Values[$Name]
}

function Get-Endpoint {
    param([string]$JdbcUrl, [string]$ExpectedScheme, [int]$DefaultPort)
    if ($JdbcUrl -notmatch '^jdbc:(?<rest>.+)$') { throw 'Database URL must be a JDBC URL.' }
    $uri = [Uri]$Matches.rest
    if ($uri.Scheme -ne $ExpectedScheme -or [string]::IsNullOrWhiteSpace($uri.Host)) { throw "Database URL must use the $ExpectedScheme scheme and include a host." }
    $database = $uri.AbsolutePath.TrimStart('/')
    if ([string]::IsNullOrWhiteSpace($database) -or $database.Contains('/')) { throw 'Database URL must identify exactly one database.' }
    return @{ Host = $uri.Host; Port = if ($uri.IsDefaultPort) { $DefaultPort } else { $uri.Port }; Database = $database }
}

function Find-MySqlClient {
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $service = Get-CimInstance Win32_Service | Where-Object { $_.Name -match 'mysql' -and $_.State -eq 'Running' } | Select-Object -First 1
    if ($service -and $service.PathName -match '^\s*"(?<exe>[^"]*mysqld\.exe)"|^\s*(?<exe>[^\s]*mysqld\.exe)') {
        $candidate = Join-Path (Split-Path $Matches.exe -Parent) 'mysql.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    throw 'mysql.exe was not found on PATH or beside a running MySQL server executable.'
}

function ConvertTo-NativeArgument {
    param([string]$Value)
    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.Append([char]34)
    $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq [char]92) {
            $slashes++
            continue
        }
        if ($character -eq [char]34) {
            [void]$builder.Append([string]::new([char]92, (2 * $slashes) + 1))
            [void]$builder.Append([char]34)
            $slashes = 0
            continue
        }
        if ($slashes -gt 0) { [void]$builder.Append([string]::new([char]92, $slashes)) }
        [void]$builder.Append($character)
        $slashes = 0
    }
    if ($slashes -gt 0) { [void]$builder.Append([string]::new([char]92, 2 * $slashes)) }
    [void]$builder.Append([char]34)
    return $builder.ToString()
}

function Invoke-DbClient {
    param([string]$Executable, [string[]]$Arguments, [string]$Sql, [string]$Password, [string]$Engine, [switch]$AllowFailure)
    $originalPreference = $ErrorActionPreference
    $process = $null
    try {
        # Use redirected .NET process streams rather than PowerShell's native stderr
        # error records. This keeps expected database denials available for SQLSTATE checks.
        $ErrorActionPreference = 'Continue'
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $Executable
        $startInfo.Arguments = (@($Arguments | ForEach-Object { ConvertTo-NativeArgument ([string]$_) }) -join ' ')
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardInput = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        [void]$startInfo.EnvironmentVariables.Remove('PGPASSWORD')
        [void]$startInfo.EnvironmentVariables.Remove('MYSQL_PWD')
        if ($Engine -eq 'postgres') { $startInfo.EnvironmentVariables['PGPASSWORD'] = $Password }
        else { $startInfo.EnvironmentVariables['MYSQL_PWD'] = $Password }

        $process = [System.Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        if (-not $process.Start()) { throw "$Engine client process could not be started." }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $exitCode = $process.ExitCode
        $message = ($stdout + [Environment]::NewLine + $stderr).Trim()
        foreach ($secret in $script:secretValues) {
            if ($secret.Length -gt 0) { $message = $message.Replace($secret, '[REDACTED]') }
        }
        if ($exitCode -ne 0 -and -not $AllowFailure) {
            throw "$Engine client exited with code $exitCode. $message"
        }
        return @{ ExitCode = $exitCode; Output = $message }
    }
    finally {
        if ($process) { $process.Dispose() }
        $ErrorActionPreference = $originalPreference
    }
}

function Invoke-Postgres {
    param($Endpoint, [string]$Username, [string]$Password, [string]$Sql, [switch]$AllowFailure)
    $args = @('-X', '-t', '-A', '-q', '--no-password', '--set=ON_ERROR_STOP=1', '--set=VERBOSITY=verbose', '--host', $Endpoint.Host, '--port', [string]$Endpoint.Port, '--username', $Username, '--dbname', $Endpoint.Database)
    return Invoke-DbClient -Executable 'psql.exe' -Arguments $args -Sql $Sql -Password $Password -Engine postgres -AllowFailure:$AllowFailure
}

function Invoke-MySql {
    param([string]$Executable, $Endpoint, [string]$Username, [string]$Password, [string]$Sql, [switch]$AllowFailure)
    $args = @('--batch', '--skip-column-names', '--protocol=TCP', '--host', $Endpoint.Host, '--port', [string]$Endpoint.Port, '--user', $Username, '--database', $Endpoint.Database, '--default-character-set=utf8mb4')
    return Invoke-DbClient -Executable $Executable -Arguments $args -Sql $Sql -Password $Password -Engine mysql -AllowFailure:$AllowFailure
}

function Prompt-RuntimePassword {
    param([string]$Label)
    $secure = Read-Host -Prompt "Enter the $Label runtime password (input hidden)" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ([string]::IsNullOrWhiteSpace($plain)) { throw "$Label runtime password cannot be empty." }
        if ($plain -match '[\x00-\x1F\x7F]') { throw "$Label runtime password cannot contain control characters." }
        return $plain
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $secure.Dispose()
    }
}

function Set-EnvRuntimeValues {
    param([string]$Path, [hashtable]$Updates)
    $lines = [System.Collections.Generic.List[string]]::new()
    $seen = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=') {
            $key = $Matches[1]
            if ($Updates.ContainsKey($key)) {
                $slash = [string][char]92
                $quote = [string][char]34
                $encoded = ([string]$Updates[$key]).Replace($slash, $slash + $slash).Replace($quote, $slash + $quote)
                $lines.Add("$key=`"$encoded`"")
                $seen[$key] = $true
                continue
            }
        }
        $lines.Add($line)
    }
    foreach ($key in $Updates.Keys) {
        if (-not $seen.ContainsKey($key)) {
            $slash = [string][char]92
            $quote = [string][char]34
            $encoded = ([string]$Updates[$key]).Replace($slash, $slash + $slash).Replace($quote, $slash + $quote)
            $lines.Add("$key=`"$encoded`"")
        }
    }
    $temp = Join-Path (Split-Path $Path -Parent) ('.env.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [System.IO.File]::WriteAllLines($temp, $lines, [System.Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temp -Destination $Path -Force
    }
    finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force }
    }
}

function Test-ReadOnlyAccess {
    param([string]$Engine, [string]$Client, $Endpoint, [string]$Username, [string]$Password)
    if ($Engine -eq 'postgres') {
        $select = Invoke-Postgres $Endpoint $Username $Password "SELECT COUNT(*) FROM public.customers;"
    }
    else {
        $select = Invoke-MySql $Client $Endpoint $Username $Password 'SELECT COUNT(*) FROM customers;'
    }
    $numericLines = @($select.Output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\A[0-9]+\z' })
    [long]$count = 0
    if ($numericLines.Count -ne 1 -or -not [long]::TryParse($numericLines[0], [Globalization.NumberStyles]::None, [Globalization.CultureInfo]::InvariantCulture, [ref]$count)) {
        throw "$Engine read-only account connected, but SELECT verification did not return exactly one numeric row count."
    }

    $tests = if ($Engine -eq 'postgres') {
        @(
            @{ Name='INSERT'; Sql="BEGIN; INSERT INTO public.customers(name,email,company,industry) SELECT name,'readonly-check-' || md5(random()::text) || '@invalid.example',company,industry FROM public.customers WHERE false; ROLLBACK;" },
            @{ Name='UPDATE'; Sql='BEGIN; UPDATE public.customers SET name=name WHERE id=-1; ROLLBACK;' },
            @{ Name='DELETE'; Sql='BEGIN; DELETE FROM public.customers WHERE id=-1; ROLLBACK;' }
        )
    }
    else {
        @(
            @{ Name='INSERT'; Sql='START TRANSACTION; INSERT INTO customers(name,email,city,country,created_at) SELECT name,CONCAT("readonly-check-",UUID(),"@invalid.example"),city,country,created_at FROM customers WHERE 0; ROLLBACK;' },
            @{ Name='UPDATE'; Sql='START TRANSACTION; UPDATE customers SET name=name WHERE id=0; ROLLBACK;' },
            @{ Name='DELETE'; Sql='START TRANSACTION; DELETE FROM customers WHERE id=0; ROLLBACK;' }
        )
    }
    $results = @{}
    foreach ($test in $tests) {
        if ($Engine -eq 'postgres') { $attempt = Invoke-Postgres $Endpoint $Username $Password $test.Sql -AllowFailure }
        else { $attempt = Invoke-MySql $Client $Endpoint $Username $Password $test.Sql -AllowFailure }
        $denied = if ($Engine -eq 'postgres') {
            $attempt.ExitCode -ne 0 -and $attempt.Output -match '(?i)\b42501\b'
        }
        else {
            $attempt.ExitCode -ne 0 -and $attempt.Output -match '(?i)\bERROR\s+(?:1142|1143)\s+\('
        }
        if (-not $denied) {
            if ($attempt.ExitCode -eq 0) { throw "$Engine $($test.Name) unexpectedly succeeded; verification failed." }
            throw "$Engine $($test.Name) failed for a reason other than a recognized permission denial: $($attempt.Output)"
        }
        $results[$test.Name] = 'denied'
    }
    return @{ Rows = $count; Writes = $results }
}

$values = Read-EnvFile $envPath
$pgEndpoint = Get-Endpoint (Get-RequiredValue $values 'CRM_DB_URL') 'postgresql' 5432
$myEndpoint = Get-Endpoint (Get-RequiredValue $values 'ECOMMERCE_DB_URL') 'mysql' 3306
if ($pgEndpoint.Database -ne 'enterprise_crm' -or $myEndpoint.Database -ne 'enterprise_ecommerce') { throw 'Database URLs must target enterprise_crm and enterprise_ecommerce respectively.' }
$mysqlClient = Find-MySqlClient

try {
    $pgUser = if ($values.ContainsKey('CRM_DB_USERNAME') -and -not [string]::IsNullOrWhiteSpace($values.CRM_DB_USERNAME)) { $values.CRM_DB_USERNAME } else { 'crm_reader' }
    $myUser = if ($values.ContainsKey('ECOMMERCE_DB_USERNAME') -and -not [string]::IsNullOrWhiteSpace($values.ECOMMERCE_DB_USERNAME)) { $values.ECOMMERCE_DB_USERNAME } else { 'ecommerce_reader' }
    if ($pgUser -ne 'crm_reader' -or $myUser -ne 'ecommerce_reader') { throw 'Runtime usernames in .env must be crm_reader and ecommerce_reader.' }

    $pgPassword = if ($values.ContainsKey('CRM_DB_PASSWORD') -and -not [string]::IsNullOrWhiteSpace($values.CRM_DB_PASSWORD)) { $values.CRM_DB_PASSWORD } else { Prompt-RuntimePassword 'CRM PostgreSQL' }
    $myPassword = if ($values.ContainsKey('ECOMMERCE_DB_PASSWORD') -and -not [string]::IsNullOrWhiteSpace($values.ECOMMERCE_DB_PASSWORD)) { $values.ECOMMERCE_DB_PASSWORD } else { Prompt-RuntimePassword 'E-commerce MySQL' }
    $script:secretValues.Add($pgPassword)
    $script:secretValues.Add($myPassword)

    $pgVerification = Test-ReadOnlyAccess 'postgres' $null $pgEndpoint $pgUser $pgPassword
    Write-Output "PostgreSQL runtime SELECT: passed ($($pgVerification.Rows) customer rows). INSERT/UPDATE/DELETE: denied."
    $myVerification = Test-ReadOnlyAccess 'mysql' $mysqlClient $myEndpoint $myUser $myPassword
    Write-Output "MySQL runtime SELECT: passed ($($myVerification.Rows) customer rows). INSERT/UPDATE/DELETE: denied."

    Set-EnvRuntimeValues $envPath @{ CRM_DB_USERNAME = $pgUser; CRM_DB_PASSWORD = $pgPassword; ECOMMERCE_DB_USERNAME = $myUser; ECOMMERCE_DB_PASSWORD = $myPassword }
    Write-Output 'Runtime credentials saved to the ignored local .env file after both account checks passed.'
}
catch {
    $message = $_.Exception.Message
    foreach ($secret in $script:secretValues) { if ($secret.Length -gt 0) { $message = $message.Replace($secret, '[REDACTED]') } }
    [Console]::Error.WriteLine("Provisioning stopped: $message")
    exit 1
}
